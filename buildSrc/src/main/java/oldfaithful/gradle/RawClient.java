package oldfaithful.gradle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A b1.7.3 client (protocol 14) speaking raw packets, for :harness:multiplayerTest: logs in to an offline-mode server,
 * keeps reading everything the server sends (so the server's send queue never overflows), answers its position as a
 * real client does, and can then send whatever a test needs, honest or not.
 */
final class RawClient implements AutoCloseable {
	final String name;
	private final Socket socket;
	private final DataOutputStream out;
	private final DataInputStream in;
	private final Thread reader;
	private final Object lock = new Object();

	private boolean loggedIn;
	private int entityId = -1;
	private String kickReason;
	private boolean closed;
	private double[] position; // x, feet y, eye y, z, as last set by the server
	private float yaw, pitch;
	private final List<String> chat = new ArrayList<>();
	private final List<Received> received = new ArrayList<>();

	/**
	 * A packet the server sent that a test may ask about: player spawns (20: entity id, name), entity destroys (29: id),
	 * chunk data (51: block x, z; d 1 if a whole chunk), block changes (53: x, y, z, block), sign texts (130: x, y, z, the lines), respawns (9),
	 * health (8: health), velocities (28: entity id, x, y, z in 1/8000 blocks a tick).
	 */
	record Received(long at, int id, int a, int b, int c, int d, String text) {
	}

	RawClient(int port, String name) throws IOException {
		this.name = name;
		socket = new Socket();
		socket.connect(new InetSocketAddress("127.0.0.1", port), 10_000);
		socket.setTcpNoDelay(true);
		out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
		in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		reader = new Thread(this::readLoop, "raw-client " + name);
		reader.setDaemon(true);
	}

	/** Handshake and login; returns null once in the world, or the reason the server gave for refusing. */
	String login(long timeoutSeconds) throws IOException, InterruptedException {
		synchronized (out) {
			out.write(2);
			writeString(out, name);
			out.flush();
		}
		int id = in.read();
		if (id == 0xFF) return readString(in);
		if (id != 2) throw new IOException("expected the handshake, got packet " + id);
		readString(in); // "-" in offline mode
		synchronized (out) {
			out.write(1);
			out.writeInt(14);
			writeString(out, name);
			out.writeLong(0);
			out.writeByte(0);
			out.flush();
		}
		reader.start();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		synchronized (lock) {
			while (position == null && kickReason == null && !closed) {
				long left = deadline - System.nanoTime();
				if (left <= 0) throw new IOException(name + ": no position from the server within " + timeoutSeconds + " s");
				lock.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)));
			}
			if (kickReason != null) return kickReason;
			if (position == null) return "connection closed";
		}
		// The server drops a client that sends nothing for a minute.
		Thread keepAlive = new Thread(() -> {
			try {
				while (!isClosed()) {
					Thread.sleep(1000);
					keepAlive();
				}
			} catch (IOException | InterruptedException ignored) {
				// closed
			}
		}, "raw-client keep-alive " + name);
		keepAlive.setDaemon(true);
		keepAlive.start();
		return null;
	}

	/** Sends the login packet again (a second one on the same connection). */
	void sendHelloAgain() throws IOException {
		synchronized (out) {
			out.write(1);
			out.writeInt(14);
			writeString(out, name);
			out.writeLong(0);
			out.writeByte(0);
			out.flush();
		}
	}

	/** This player's own entity id, from the login packet. */
	int entityId() {
		synchronized (lock) {
			return entityId;
		}
	}

	double[] position() {
		synchronized (lock) {
			return position == null ? null : position.clone();
		}
	}

	/** A full move (packet 13): x, feet y, eye y, z. */
	void move(double x, double feet, double eyes, double z, float yaw, float pitch, boolean onGround) throws IOException {
		synchronized (out) {
			out.write(13);
			out.writeDouble(x);
			out.writeDouble(feet);
			out.writeDouble(eyes);
			out.writeDouble(z);
			out.writeFloat(yaw);
			out.writeFloat(pitch);
			out.writeBoolean(onGround);
			out.flush();
		}
	}

	/** Many full moves in one write, as a lagged client's queued moves arrive: each {x, feet y, z}. */
	void moveBurst(java.util.List<double[]> positions) throws IOException {
		synchronized (out) {
			for (double[] p : positions) {
				out.write(13);
				out.writeDouble(p[0]);
				out.writeDouble(p[1]);
				out.writeDouble(p[1] + 1.62);
				out.writeDouble(p[2]);
				out.writeFloat(yaw);
				out.writeFloat(pitch);
				out.writeBoolean(false);
			}
			out.flush();
		}
	}

	/** What a riding client sends each tick: its motion where the position goes, y and stance -999. */
	void ridingMove(double motionX, double motionZ) throws IOException {
		move(motionX, -999, -999, motionZ, yaw, pitch, false);
	}

	void chat(String message) throws IOException {
		synchronized (out) {
			out.write(3);
			writeString(out, message);
			out.flush();
		}
	}

	void keepAlive() throws IOException {
		synchronized (out) {
			out.write(0);
			out.flush();
		}
	}

	/** Writes {@code bytes} of keep-alives as fast as the socket takes them; returns how many were written in time. */
	long flood(long bytes, long timeoutMillis) throws InterruptedException {
		long[] written = {0};
		Thread t = new Thread(() -> {
			byte[] chunk = new byte[1 << 16];
			try {
				while (written[0] < bytes) {
					int n = (int) Math.min(chunk.length, bytes - written[0]);
					synchronized (out) {
						out.write(chunk, 0, n);
						out.flush();
					}
					synchronized (written) {
						written[0] += n;
					}
				}
			} catch (IOException ignored) {
				// closed
			}
		}, "raw-client flood");
		t.setDaemon(true);
		t.start();
		t.join(timeoutMillis);
		synchronized (written) {
			return written[0];
		}
	}

	boolean sawChat(String text) {
		synchronized (lock) {
			for (String line : chat) if (line.contains(text)) return true;
			return false;
		}
	}

	/** The server's disconnect reason, waiting up to {@code timeoutSeconds} for it; null if none came. */
	String awaitKick(long timeoutSeconds) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		synchronized (lock) {
			while (kickReason == null && !closed) {
				long left = deadline - System.nanoTime();
				if (left <= 0) return null;
				lock.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)));
			}
			return kickReason != null ? kickReason : "(closed without a reason)";
		}
	}

	boolean isClosed() {
		synchronized (lock) {
			return closed || kickReason != null;
		}
	}

	@Override
	public void close() {
		try {
			socket.close();
		} catch (IOException ignored) {
		}
	}

	private void note(int id, int a, int b, int c, int d, String text) {
		synchronized (lock) {
			received.add(new Received(System.nanoTime(), id, a, b, c, d, text));
			lock.notifyAll();
		}
	}

	/** What the server has sent so far of the packets {@link Received} lists. */
	List<Received> received() {
		synchronized (lock) {
			return new ArrayList<>(received);
		}
	}

	/** Waits up to {@code timeoutSeconds} for a received packet matching {@code test}; null if none came. */
	Received await(java.util.function.Predicate<Received> test, double timeoutSeconds) throws InterruptedException {
		long deadline = System.nanoTime() + (long) (timeoutSeconds * 1e9);
		synchronized (lock) {
			while (true) {
				for (Received r : received) if (test.test(r)) return r;
				long left = deadline - System.nanoTime();
				if (left <= 0 || closed || kickReason != null) return null;
				lock.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(left)));
			}
		}
	}

	/** The entity id the server gave the player named {@code name}, as seen by this client; -1 if not seen. */
	int playerEntity(String name) {
		synchronized (lock) {
			for (Received r : received) if (r.id() == 20 && r.text().equals(name)) return r.a();
			return -1;
		}
	}

	/** Packet 14: start (0) or finish (2) mining a block. */
	void playerAction(int action, int x, int y, int z, int face) throws IOException {
		synchronized (out) {
			out.write(14);
			out.write(action);
			out.writeInt(x);
			out.write(y);
			out.writeInt(z);
			out.write(face);
			out.flush();
		}
	}

	/** Packet 9, as the death screen's Respawn button sends it. */
	void respawn() throws IOException {
		synchronized (out) {
			out.write(9);
			out.writeByte(0);
			out.flush();
		}
	}

	/** Sends the last position the server set back {@code times} times, {@code intervalMillis} apart (chunks flow). */
	void stand(int times, long intervalMillis) throws IOException, InterruptedException {
		for (int i = 0; i < times && !isClosed(); i++) {
			double[] p = position();
			move(p[0], p[1], p[2], p[3], yaw, pitch, false);
			Thread.sleep(intervalMillis);
		}
	}

	private void readLoop() {
		try {
			while (true) {
				int id = in.read();
				if (id < 0) break;
				readPacket(id);
			}
		} catch (IOException ignored) {
			// closed
		} finally {
			synchronized (lock) {
				closed = true;
				lock.notifyAll();
			}
		}
	}

	private void readPacket(int id) throws IOException {
		switch (id) {
			case 0: break;
			case 1: {
				int self = in.readInt();
				readString(in);
				in.readLong();
				in.readByte();
				synchronized (lock) {
					loggedIn = true;
					entityId = self;
				}
				break;
			}
			case 2: readString(in); break;
			case 3: {
				String message = readString(in);
				synchronized (lock) {
					chat.add(message);
					lock.notifyAll();
				}
				break;
			}
			case 4: in.readLong(); break;
			case 5: in.readInt(); in.readShort(); in.readShort(); in.readShort(); break;
			case 6: skip(12); break;
			case 8: note(8, in.readShort(), 0, 0, 0, null); break;
			case 9: in.readByte(); note(9, 0, 0, 0, 0, null); break;
			case 10: in.readByte(); break;
			case 11: skip(33); break;
			case 12: skip(9); break;
			case 13: {
				double x = in.readDouble(), eyes = in.readDouble(), feet = in.readDouble(), z = in.readDouble();
				float yaw = in.readFloat(), pitch = in.readFloat();
				boolean onGround = in.readBoolean();
				// A real client takes the position and sends it straight back (the server waits for that before it
				// accepts moves); here as feet, eyes, the order clients send.
				move(x, feet, eyes, z, yaw, pitch, onGround);
				synchronized (lock) {
					position = new double[] {x, feet, eyes, z};
					this.yaw = yaw;
					this.pitch = pitch;
					lock.notifyAll();
				}
				break;
			}
			case 17: in.readInt(); in.readByte(); in.readInt(); in.readByte(); in.readInt(); break;
			case 18: in.readInt(); in.readByte(); break;
			case 20: {
				int entity = in.readInt();
				String player = readString(in);
				skip(12 + 2 + 2);
				note(20, entity, 0, 0, 0, player);
				break;
			}
			case 21: in.readInt(); in.readShort(); in.readByte(); in.readShort(); skip(12 + 3); break;
			case 22: skip(8); break;
			case 23: {
				in.readInt(); in.readByte(); skip(12);
				if (in.readInt() > 0) skip(6);
				break;
			}
			case 24: in.readInt(); in.readByte(); skip(12 + 2); skipTracked(); break;
			case 25: in.readInt(); readString(in); skip(16); break;
			case 28: note(28, in.readInt(), in.readShort(), in.readShort(), in.readShort(), null); break;
			case 29: note(29, in.readInt(), 0, 0, 0, null); break;
			case 30: skip(4); break;
			case 31: skip(4 + 3); break;
			case 32: skip(4 + 2); break;
			case 33: skip(4 + 5); break;
			case 34: skip(4 + 12 + 2); break;
			case 38: skip(5); break;
			case 39: skip(8); break;
			case 40: in.readInt(); skipTracked(); break;
			case 50: { int x = in.readInt(), z = in.readInt(); note(50, x, z, in.readByte(), 0, null); break; }
			case 51: {
				int x = in.readInt();
				in.readShort();
				int z = in.readInt();
				int sx = in.readUnsignedByte(), sy = in.readUnsignedByte(), sz = in.readUnsignedByte();
				skip(in.readInt());
				// d: 1 for a whole chunk (16 x 128 x 16), 0 for a region of changed blocks
				note(51, x, z, 0, sx == 15 && sy == 127 && sz == 15 ? 1 : 0, null);
				break;
			}
			case 52: {
				int cx = in.readInt(), cz = in.readInt();
				int n = in.readShort() & 0xffff;
				skip(n * 4L);
				note(52, cx, cz, n, 0, null);
				break;
			}
			case 53: {
				int x = in.readInt(), y = in.readByte(), z = in.readInt(), block = in.readByte() & 0xFF;
				in.readByte();
				note(53, x, y, z, block, null);
				break;
			}
			case 54: skip(4 + 2 + 4 + 2); break;
			case 60: {
				skip(24 + 4);
				int n = in.readInt();
				skip(n * 3L);
				break;
			}
			case 61: skip(4 + 4 + 1 + 4 + 4); break;
			case 70: in.readByte(); break;
			case 71: skip(4 + 1 + 12); break;
			case 100: in.readByte(); in.readByte(); in.readUTF(); in.readByte(); break;
			case 101: in.readByte(); break;
			case 103: in.readByte(); in.readShort(); skipStack(); break;
			case 104: {
				in.readByte();
				int n = in.readShort();
				for (int i = 0; i < n; i++) skipStack();
				break;
			}
			case 105: skip(5); break;
			case 106: skip(4); break;
			case 130: {
				int x = in.readInt(), y = in.readShort(), z = in.readInt();
				StringBuilder lines = new StringBuilder();
				for (int i = 0; i < 4; i++) lines.append(i == 0 ? "" : "|").append(readString(in));
				note(130, x, y, z, 0, lines.toString());
				break;
			}
			case 131: skip(4); skip(in.readUnsignedByte()); break;
			case 200: skip(5); break;
			case 255: {
				String reason = readString(in);
				synchronized (lock) {
					kickReason = reason;
					lock.notifyAll();
				}
				throw new EOFException("kicked");
			}
			default: throw new IOException(name + ": unknown packet " + id + " from the server");
		}
	}

	private void skipStack() throws IOException {
		if (in.readShort() >= 0) skip(3);
	}

	private void skipTracked() throws IOException {
		for (int b = in.readUnsignedByte(); b != 127; b = in.readUnsignedByte()) {
			switch ((b & 0xe0) >> 5) {
				case 0: skip(1); break;
				case 1: skip(2); break;
				case 2: case 3: skip(4); break;
				case 4: readString(in); break;
				case 5: skip(5); break;
				case 6: skip(12); break;
				default: throw new IOException("bad tracked data type");
			}
		}
	}

	private void skip(long n) throws IOException {
		while (n > 0) {
			long s = in.skip(n);
			if (s <= 0) {
				in.readByte();
				s = 1;
			}
			n -= s;
		}
	}

	static void writeString(DataOutputStream out, String s) throws IOException {
		out.writeShort(s.length());
		out.writeChars(s);
	}

	static String readString(DataInputStream in) throws IOException {
		int length = in.readShort();
		if (length < 0 || length > 32767) throw new IOException("bad string length " + length);
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) sb.append(in.readChar());
		return sb.toString();
	}
}
