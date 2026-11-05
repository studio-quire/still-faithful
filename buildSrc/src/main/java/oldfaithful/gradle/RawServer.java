package oldfaithful.gradle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.Deflater;

/**
 * A b1.7.3 server speaking raw packets to a real client, for the harness: it plays a hello captured from a real server, so the client takes it for one with the
 * same content, brings it into a world of nothing, and then sends each case's attack. Each case is a connection; the last is turned away with
 * {@link #DONE}, which is the client's sign to stop.
 */
final class RawServer implements AutoCloseable {
	static final String DONE = "harness-done";
	private static final int MAGIC_PREFIX = RawClient.CORE_PREFIX;

	/** One attack: {@code hello} if it replaces the hello, {@code attack} if it comes once the client is in the world. */
	record Case(String name, boolean mustDisconnect, byte[] hello, Attack attack) {
	}

	interface Attack {
		void send(DataOutputStream out) throws IOException;
	}

	private final ServerSocket socket;
	private final byte[] hello;

	RawServer(byte[] hello) throws IOException {
		this.hello = hello;
		socket = new ServerSocket();
		socket.bind(new InetSocketAddress("127.0.0.1", 0));
		socket.setSoTimeout(60_000);
	}

	int port() {
		return socket.getLocalPort();
	}

	/** Plays every case in turn; {@code result} hears each one's name and whether the client left by itself. */
	void play(List<Case> cases, Result result) throws IOException {
		for (Case c : cases) {
			try (Socket s = socket.accept()) {
				result.heard(c.name, one(s, c), c.mustDisconnect);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		try (Socket s = socket.accept()) {
			DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
			in.read();
			RawClient.readString(in);
			kick(out, DONE);
			Thread.sleep(500);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	interface Result {
		void heard(String name, boolean left, boolean mustDisconnect);
	}

	private static void kick(DataOutputStream out, String reason) throws IOException {
		out.write(255);
		RawClient.writeString(out, reason);
		out.flush();
	}

	/** Whether the client left of its own accord. Anything going wrong before the attack is this end's fault, and is thrown. */
	private boolean one(Socket s, Case c) throws IOException, InterruptedException {
		s.setTcpNoDelay(true);
		DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
		if (in.read() != 2) throw new IOException("no handshake");
		RawClient.readString(in);
		out.write(2);
		RawClient.writeString(out, "-");
		out.flush();
		if (in.read() != 1) throw new IOException("no login");
		in.readInt();
		RawClient.readString(in);
		long seed = in.readLong();
		in.readByte();
		if (seed != RawClient.CORE_MAGIC) throw new IOException("a client without the core");
		channel(out, 0, c.hello != null ? c.hello : hello);
		out.flush();
		if (c.hello != null) return awaitLeaving(s, in, out);
		if (in.read() != 250) throw new IOException("no reply to the hello");
		if (in.readInt() != MAGIC_PREFIX) throw new IOException("a stranger's 250");
		in.readUnsignedShort();
		in.readFully(new byte[in.readInt()]);
		out.write(1);
		out.writeInt(1000);
		RawClient.writeString(out, "");
		out.writeLong(0);
		out.writeByte(0);
		out.write(6);
		out.writeInt(0);
		out.writeInt(100);
		out.writeInt(0);
		out.write(50);
		out.writeInt(0);
		out.writeInt(0);
		out.writeBoolean(true);
		chunk(out, 0, 0, new byte[Wire.WHOLE]);
		out.write(13);
		out.writeDouble(8);
		out.writeDouble(101.62);
		out.writeDouble(100);
		out.writeDouble(8);
		out.writeFloat(0);
		out.writeFloat(0);
		out.writeBoolean(false);
		out.flush();
		Thread.sleep(1200);
		try {
			c.attack.send(out);
			out.flush();
		} catch (IOException e) {
			return true;
		}
		return awaitLeaving(s, in, out);
	}

	/** Drains what the client says for a few seconds; true if it left. Then sends it on its way, so it comes back for the next case. */
	private boolean awaitLeaving(Socket s, DataInputStream in, DataOutputStream out) throws IOException {
		s.setSoTimeout(100);
		long until = System.currentTimeMillis() + 3000;
		byte[] sink = new byte[4096];
		while (System.currentTimeMillis() < until) {
			try {
				if (in.read(sink) < 0) return true;
			} catch (java.net.SocketTimeoutException e) {
				// It's still there.
			} catch (IOException e) {
				return true;
			}
		}
		try {
			kick(out, "next");
			s.setSoTimeout(1000);
			while (in.read(sink) >= 0) {
				// Until it goes.
			}
		} catch (IOException e) {
			// Gone.
		}
		return false;
	}

	static void channel(DataOutputStream out, int number, byte[] data) throws IOException {
		out.write(250);
		out.writeInt(MAGIC_PREFIX);
		out.writeShort(number);
		out.writeInt(data.length);
		out.write(data);
	}

	/** Packet 51, a whole chunk: {@code tail} goes after the vanilla section, as the overlay. Zeros in the vanilla section are air. */
	static void chunk(DataOutputStream out, int cx, int cz, byte[] tail) throws IOException {
		byte[] raw = new byte[Wire.VANILLA + tail.length];
		System.arraycopy(tail, 0, raw, Wire.VANILLA, tail.length);
		byte[] packed = deflate(raw);
		out.write(51);
		out.writeInt(cx * 16);
		out.writeShort(0);
		out.writeInt(cz * 16);
		out.write(15);
		out.write(127);
		out.write(15);
		out.writeInt(packed.length);
		out.write(packed);
	}

	static byte[] deflate(byte[] raw) {
		Deflater deflater = new Deflater();
		deflater.setInput(raw);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
		deflater.end();
		return out.toByteArray();
	}

	/** Sizes from {@code core.impl.content.Wire}. */
	static final class Wire {
		static final int CELLS = 16 * 128 * 16, VANILLA = CELLS * 5 / 2, WHOLE = CELLS * 2;

		private Wire() {
		}
	}

	static byte[] bytes(Consumer<DataOutputStream> write) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		write.accept(new DataOutputStream(bytes));
		return bytes.toByteArray();
	}

	@Override
	public void close() throws IOException {
		socket.close();
	}
}
