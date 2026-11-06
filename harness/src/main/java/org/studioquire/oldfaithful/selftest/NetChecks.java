package org.studioquire.oldfaithful.selftest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

import net.minecraft.network.Connection;
import net.minecraft.network.NetworkHandler;
import net.minecraft.network.packet.Packet;

import org.studioquire.oldfaithful.hardening.login.Usernames;
import org.studioquire.oldfaithful.hardening.packet.ConnectionLimits;
import org.studioquire.oldfaithful.hardening.packet.MoveChecks;
import org.studioquire.oldfaithful.selftest.mixin.ConnectionAccessor;

/**
 * Phase 6's hardening, checked in-process on both sides: login names, movement numbers, the
 * bounded read queue on a real loopback connection, and, on the client, that a packet whose handling throws ends the
 * connection instead of the game. The live attacks against a running server are in :harness:multiplayerTest.
 */
final class NetChecks {
	private final SelfTest t;

	NetChecks(SelfTest t) {
		this.t = t;
	}

	void run(boolean client) {
		names();
		moves();
		backpressure(true, ConnectionLimits.SERVER_MAX_PACKETS + 5000);
		if (client) {
			backpressure(false, ConnectionLimits.CLIENT_MAX_PACKETS + 5000);
			chunkBackpressure();
			try {
				heavyBackpressure("explosion packets listing 262,144 blocks", explosionPacket(262_144), 6);
				heavyBackpressure("entity data packets of 255 64-character strings", trackedStringsPacket(255), 80);
			} catch (IOException e) {
				t.fail("client read queue: heavy packets", e);
			}
			clientApplyGuard();
		}
		serverApplyUnchanged();
	}

	private void names() {
		String[] good = {"Notch", "jeb_", "a", "Steve Smith", "Ünïcødé", "x.y", "CONSOLE", "COM10", "...."};
		String[] bad = {"../level", "..", ".", "a/b", "a\\b", "C:x", "con", "CON.txt", "nul", "Com1", "LPT¹", "",
				"   ", "tab\there", "nul\u0000", "a?b", "a*b", "a|b", "<x>", "\"q\""};
		boolean ok = true;
		for (String n : good) if (Usernames.problem(n) != null) { ok = false; System.out.println("  accepted name refused: " + n); }
		for (String n : bad) if (Usernames.problem(n) == null) { ok = false; System.out.println("  unsafe name accepted: " + n); }
		t.check("login names: " + good.length + " ordinary names pass, " + bad.length + " unsafe ones are refused", ok);
	}

	private void moves() {
		boolean ok = MoveChecks.problem(true, 10.5, 64, 65.62, -3.25, true, 90, 10) == null
				&& MoveChecks.problem(true, 0.31, -999, -999, -0.2, true, 0, 0) == null // riding, honest
				&& MoveChecks.problem(false, Double.NaN, 0, 0, 0, false, Float.NaN, 0) == null // fields unused
				&& MoveChecks.problem(true, Double.NaN, 64, 65.62, 0, false, 0, 0) != null
				&& MoveChecks.problem(true, 0, 64, Double.POSITIVE_INFINITY, 0, false, 0, 0) != null
				&& MoveChecks.problem(true, 1e7, -999, -999, 1e7, false, 0, 0) != null
				&& MoveChecks.problem(true, 0, -999, -999, -11, false, 0, 0) != null
				&& MoveChecks.problem(false, 0, 0, 0, 0, true, Float.NaN, 0) != null
				&& MoveChecks.inputProblem(0.98f, -0.98f, 30, 270) == null
				&& MoveChecks.inputProblem(0, 0, Float.NaN, 0) != null
				&& MoveChecks.inputProblem(Float.POSITIVE_INFINITY, 0, 0, 0) != null;
		t.check("movement: honest moves and rider input pass; NaN, infinity and huge riding motion are refused", ok);
	}

	/** A handler that counts what it's given; optionally throws on every packet. */
	static final class Handler extends NetworkHandler {
		final boolean serverSide;
		final boolean fail;
		final AtomicInteger handled = new AtomicInteger();
		volatile String disconnectReason;

		Handler(boolean serverSide, boolean fail) {
			this.serverSide = serverSide;
			this.fail = fail;
		}

		@Override
		public boolean isServerSide() {
			return serverSide;
		}

		@Override
		public void handle(Packet packet) {
			handled.incrementAndGet();
			if (fail) throw new IllegalArgumentException("test packet failure");
		}

		@Override
		public void handleChunkData(net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket packet) {
			handle(packet);
		}

		@Override
		public void onDisconnected(String reason, Object[] args) {
			disconnectReason = reason;
		}
	}

	/** A connected pair on loopback: the game's Connection reading what {@code out} writes. */
	private static final class Pair implements AutoCloseable {
		final Connection connection;
		final Socket peer;
		final OutputStream out;

		Pair(NetworkHandler handler) throws IOException {
			try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
				peer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
				Socket accepted = listener.accept();
				connection = new Connection(accepted, "oldfaithful-selftest", handler);
			}
			peer.setSendBufferSize(1 << 16);
			out = peer.getOutputStream();
		}

		List<?> queue() {
			return ((ConnectionAccessor) connection).oldfaithful$readQueue();
		}

		@Override
		public void close() {
			connection.disconnect("disconnect.closed");
			try {
				peer.close();
			} catch (IOException ignored) {
			}
		}
	}

	/** {@code count} entity-animation packets (6 bytes each; both sides read them, and they reach handle()). */
	private static byte[] animations(int count) {
		byte[] bytes = new byte[count * 6];
		for (int i = 0; i < count; i++) {
			bytes[i * 6] = 18;
			bytes[i * 6 + 5] = 1;
		}
		return bytes;
	}

	/**
	 * More small packets than the queue may hold, written as fast as the socket takes them: the queue
	 * must stop at its bound with the reader paused, then every packet must still arrive as the game handles them.
	 */
	private void backpressure(boolean serverSide, int count) {
		String side = serverSide ? "server" : "client";
		int bound = serverSide ? ConnectionLimits.SERVER_MAX_PACKETS : ConnectionLimits.CLIENT_MAX_PACKETS;
		Handler handler = new Handler(serverSide, false);
		try (Pair pair = new Pair(handler)) {
			Thread writer = new Thread(() -> {
				try {
					pair.out.write(animations(count));
					pair.out.flush();
				} catch (IOException ignored) {
				}
			}, "oldfaithful-selftest flood");
			writer.setDaemon(true);
			writer.start();
			int peak = waitForPlateau(pair);
			boolean capped = peak == bound;
			long deadline = System.currentTimeMillis() + 60_000;
			while (handler.handled.get() < count && System.currentTimeMillis() < deadline) {
				pair.connection.tick();
				Thread.sleep(2);
			}
			t.check(side + " read queue: " + count + " packets sent, queue stopped at " + peak + " (bound " + bound
					+ "), " + handler.handled.get() + " handled, none lost",
					capped && handler.handled.get() == count && handler.disconnectReason == null);
		} catch (Throwable e) {
			t.fail(side + " read queue", e);
		}
	}

	/** Waits until the queue stops growing (the reader paused or ran out of input); returns its size. */
	private static int waitForPlateau(Pair pair) throws InterruptedException {
		int last = -1;
		int stable = 0;
		long deadline = System.currentTimeMillis() + 20_000;
		while (System.currentTimeMillis() < deadline) {
			Thread.sleep(150);
			int now = pair.queue().size();
			stable = now == last ? stable + 1 : 0;
			last = now;
			if (stable >= 4) break;
		}
		return last;
	}

	/**
	 * Chunk packets that inflate to 21 MB each from a few KB of zeros (a server could send these endlessly): the
	 * client must stop reading at its byte bound, then take the rest as the game handles them.
	 */
	private void chunkBackpressure() {
		int count = 8;
		Handler handler = new Handler(false, false);
		try (Pair pair = new Pair(handler)) {
			byte[] packet = bigChunkPacket();
			Thread writer = new Thread(() -> {
				try {
					for (int i = 0; i < count; i++) pair.out.write(packet);
					pair.out.flush();
				} catch (IOException ignored) {
				}
			}, "oldfaithful-selftest chunks");
			writer.setDaemon(true);
			writer.start();
			int peak = waitForPlateau(pair);
			// The bound is reached on the packet that crosses it: 21 MB each, so the fourth.
			long perPacket = 256L * 128 * 256 * 5 / 2;
			int expected = (int) ((ConnectionLimits.CLIENT_MAX_BYTES + perPacket - 1) / perPacket);
			long deadline = System.currentTimeMillis() + 60_000;
			while (handler.handled.get() < count && System.currentTimeMillis() < deadline) {
				pair.connection.tick();
				Thread.sleep(20);
			}
			t.check("client read queue: " + count + " chunk packets of 21 MB inflated, at most " + peak + " held at once (bound "
					+ expected + "), all " + handler.handled.get() + " handled", peak == expected && handler.handled.get() == count);
		} catch (Throwable e) {
			t.fail("client read queue (chunk bytes)", e);
		}
	}

	/**
	 * Packets whose memory is many times their declared size (an explosion's block set, entity data's entries): held
	 * to what the byte bound allows by their wire size. Unweighted by wire size, the bound held about 80 such
	 * explosions (1.4 GB) or 16,384 entity packets.
	 */
	private void heavyBackpressure(String what, byte[] packet, int count) {
		Handler handler = new Handler(false, false);
		try (Pair pair = new Pair(handler)) {
			Thread writer = new Thread(() -> {
				try {
					for (int i = 0; i < count; i++) pair.out.write(packet);
					pair.out.flush();
				} catch (IOException ignored) {
				}
			}, "oldfaithful-selftest heavy");
			writer.setDaemon(true);
			writer.start();
			int peak = waitForPlateau(pair);
			long weight = ConnectionLimits.weight(null, packet.length);
			int expected = (int) ((ConnectionLimits.CLIENT_MAX_BYTES + weight - 1) / weight);
			long deadline = System.currentTimeMillis() + 60_000;
			while (handler.handled.get() < count && System.currentTimeMillis() < deadline) {
				pair.connection.tick();
				Thread.sleep(20);
			}
			t.check("client read queue: " + count + " " + what + " (" + packet.length + " bytes each), at most " + peak
					+ " held at once (bound " + expected + "), all " + handler.handled.get() + " handled",
					peak == expected && handler.handled.get() == count);
		} catch (Throwable e) {
			t.fail("client read queue: " + what, e);
		}
	}

	private static byte[] explosionPacket(int blocks) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.write(60);
		out.writeDouble(0);
		out.writeDouble(64);
		out.writeDouble(0);
		out.writeFloat(4);
		out.writeInt(blocks);
		for (int i = 0; i < blocks; i++) {
			// Distinct positions, so the set really holds every one.
			out.writeByte(i & 0xff);
			out.writeByte((i >> 8) & 0xff);
			out.writeByte((i >> 16) & 0xff);
		}
		return bytes.toByteArray();
	}

	private static byte[] trackedStringsPacket(int entries) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.write(40);
		out.writeInt(1);
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 64; i++) text.append((char) ('a' + i % 26));
		for (int i = 0; i < entries; i++) {
			out.writeByte(4 << 5 | (i & 31));
			out.writeShort(64);
			out.writeChars(text.toString());
		}
		out.writeByte(127);
		return bytes.toByteArray();
	}

	private static byte[] bigChunkPacket() throws IOException {
		int sx = 256, sy = 128, sz = 256;
		Deflater deflater = new Deflater();
		deflater.setInput(new byte[sx * sy * sz * 5 / 2]);
		deflater.finish();
		ByteArrayOutputStream compressed = new ByteArrayOutputStream();
		byte[] buf = new byte[65536];
		while (!deflater.finished()) compressed.write(buf, 0, deflater.deflate(buf));
		deflater.end();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.write(51);
		out.writeInt(0);
		out.writeShort(0);
		out.writeInt(0);
		out.write(sx - 1);
		out.write(sy - 1);
		out.write(sz - 1);
		out.writeInt(compressed.size());
		compressed.writeTo(out);
		return bytes.toByteArray();
	}

	/** Client side: a packet that throws when handled ends the connection; the packets after it aren't handled. */
	private void clientApplyGuard() {
		Handler handler = new Handler(false, true);
		try (Pair pair = new Pair(handler)) {
			pair.out.write(animations(3));
			pair.out.flush();
			long deadline = System.currentTimeMillis() + 10_000;
			while (pair.queue().size() < 3 && System.currentTimeMillis() < deadline) Thread.sleep(20);
			pair.connection.tick();
			t.check("client: a packet that fails to apply disconnects (\"" + handler.disconnectReason + "\") after "
					+ handler.handled.get() + " packet, without an exception reaching the game",
					"disconnect.genericReason".equals(handler.disconnectReason) && handler.handled.get() == 1);
		} catch (Throwable e) {
			t.fail("client: a packet that fails to apply disconnects", e);
		}
	}

	/** Server side stays vanilla: the exception goes up to the server's own handling, which kicks the player. */
	private void serverApplyUnchanged() {
		Handler handler = new Handler(true, true);
		try (Pair pair = new Pair(handler)) {
			pair.out.write(animations(1));
			pair.out.flush();
			long deadline = System.currentTimeMillis() + 10_000;
			while (pair.queue().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20);
			boolean thrown = false;
			try {
				pair.connection.tick();
			} catch (IllegalArgumentException expected) {
				thrown = true;
			}
			t.check("server: a packet that fails to apply still reaches the server's own handler", thrown);
		} catch (Throwable e) {
			t.fail("server: a packet that fails to apply still reaches the server's own handler", e);
		}
	}
}
