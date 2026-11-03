package oldfaithful.gradle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.tasks.TaskAction;

/**
 * Boots the b1.7.3 dedicated server with our mods, headless: checks that it starts, generates a world, answers an
 * online-mode login through the hardened session check, saves, and stops cleanly, with no errors from our mixins.
 */
public abstract class ServerTestTask extends GameTestTask {
	private static final String PLAYER = "OldFaithfulTest";
	private static final String FLOOD_PLAYER = "OldFaithfulFlood";

	@TaskAction
	public void run() throws Exception {
		Path run = freshRunDir();
		int port = freePort();
		// online-mode on, so a login exercises the session-server check; a small view distance keeps the CPU load down.
		Files.writeString(run.resolve("server.properties"), String.join("\n",
				"level-name=world", "online-mode=true", "server-ip=127.0.0.1", "server-port=" + port,
				"view-distance=4", "max-players=4", "spawn-monsters=true", "spawn-animals=true", "pvp=true",
				"allow-nether=true", "") );

		List<String> failures = new ArrayList<>();
		List<String> command = javaCommand("net.fabricmc.loader.impl.launch.knot.KnotServer",
				List.of("-Xmx768m"), List.of("nogui"));
		List<String> lines;
		try (GameProcess server = new GameProcess(command, run, run.resolve("harness-output.log"))) {
			if (server.await(l -> l.contains("Done ("), 300) == null) {
				failures.add("server never finished starting");
			} else {
				String reason = tryOnlineLogin(port);
				getLogger().lifecycle("Online-mode login as {} without a session: kicked with \"{}\"", PLAYER, reason);
				if (!"Failed to verify username!".equals(reason)) failures.add("unexpected login result: " + reason);
				if (server.await(l -> l.contains("[purefaithful-hardening] Session server: " + PLAYER + " has not joined"), 30) == null) {
					failures.add("the hardened session check didn't run for the login");
				}

				// Many login packets on one connection: one session check, then the connection ends. Unpatched, each
				// started its own thread asking the session server, as fast as the client sent them.
				String flood = tryLogin(port, FLOOD_PLAYER, 50);
				getLogger().lifecycle("50 login packets on one connection as {}: kicked with \"{}\"", FLOOD_PLAYER, flood);
				if (!"Protocol error".equals(flood)) failures.add("repeated login packets: unexpected result " + flood);
				Thread.sleep(5000);
				long checks = server.lines().stream()
						.filter(l -> l.contains("[purefaithful-hardening] Session server: " + FLOOD_PLAYER + " has not joined")).count();
				getLogger().lifecycle("Session checks for {}: {}", FLOOD_PLAYER, checks);
				if (checks > 1) failures.add("repeated login packets started " + checks + " session checks");

				server.send("save-all");
				if (server.await(l -> l.contains("Save complete."), 60) == null) failures.add("save-all didn't complete");
				server.send("stop");
			}
			Integer exit = server.waitFor(120);
			if (exit == null) failures.add("server didn't stop within 2 minutes");
			else if (exit != 0) failures.add("server exited with " + exit);
			lines = server.lines();
		}

		Path world = run.resolve("world");
		if (!Files.isRegularFile(world.resolve("level.dat"))) failures.add("no world/level.dat");
		long regions;
		try (var files = Files.list(world.resolve("region"))) {
			regions = files.filter(p -> p.toString().endsWith(".mcr")).count();
		} catch (IOException e) {
			regions = 0;
		}
		if (regions == 0) failures.add("no region files saved");
		getLogger().lifecycle("World saved: level.dat and {} region files", regions);

		checkSelfTest(lines, failures);
		for (String p : problems(lines)) failures.add("log: " + p);
		report("Server test", lines, failures);
	}

	/**
	 * Logs in like a b1.7.3 client that has no session: handshake, read the server's challenge, send the login
	 * packet. The server then asks the session server, which says no, so it disconnects us; returns the reason.
	 */
	private static String tryOnlineLogin(int port) throws IOException {
		return tryLogin(port, PLAYER, 1);
	}

	/** As {@link #tryOnlineLogin}, sending the login packet {@code times} times at once. */
	private static String tryLogin(int port, String player, int times) throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("127.0.0.1", port), 10_000);
			socket.setSoTimeout(60_000);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			DataInputStream in = new DataInputStream(socket.getInputStream());

			out.write(2);
			writeString(out, player);
			out.flush();
			int id = in.read();
			if (id != 2) return "unexpected packet " + id + " instead of the handshake";
			String serverId = readString(in);
			if (serverId.equals("-")) return "server is in offline mode";

			for (int i = 0; i < times; i++) {
				out.write(1);
				out.writeInt(14);
				writeString(out, player);
				out.writeLong(0);
				out.writeByte(0);
			}
			out.flush();

			while (true) {
				id = in.read();
				if (id == -1) return "connection closed without a reason";
				if (id == 0xFF) return readString(in);
				if (id == 0) continue; // keep-alive
				return "unexpected packet " + id + " (logged in?)";
			}
		}
	}

	private static void writeString(DataOutputStream out, String s) throws IOException {
		out.writeShort(s.length());
		out.writeChars(s);
	}

	private static String readString(DataInputStream in) throws IOException {
		int length = in.readShort();
		if (length < 0 || length > 1024) throw new IOException("bad string length " + length);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) sb.append(in.readChar());
		return sb.toString();
	}

	private static int freePort() throws IOException {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}
}
