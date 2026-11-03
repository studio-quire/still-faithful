package oldfaithful.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Phase 6's live test (docs/multiplayer.md): the server package, started by its own start script with an offline-mode
 * world, and raw-packet clients of ours doing what honest players do and what attackers do. Each attack must end in
 * the expected refusal while the server stays responsive and honest play carries on.
 */
public abstract class MultiplayerTestTask extends GameTestTask {
	private static final Pattern SAFE_PLAYER_FILE = Pattern.compile("[A-Za-z0-9_]+\\.dat");

	/** local/server-package/OldFaithful-server, as :serverPackage writes it. */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@TaskAction
	public void run() throws Exception {
		Path run = freshRunDir(); // mods/ gets the self-test mod
		Path pkg = getServerPackage().get().getAsFile().toPath();
		try (Stream<Path> walk = Files.walk(pkg)) {
			for (Path p : walk.toList()) {
				Path target = run.resolve(pkg.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		Files.copy(getGameJar().get().getAsFile().toPath(), run.resolve("minecraft_server.jar"));
		int port = freePort();
		Files.writeString(run.resolve("server.properties"), String.join("\n",
				"level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port,
				"view-distance=3", "max-players=10", "spawn-monsters=false", "spawn-animals=false", "pvp=true",
				// So the movement scenarios can travel through the air, clear of terrain, without the floating kick.
				"allow-flight=true", ""));

		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		// By full path: with NoDefaultCurrentDirectoryInExePath set, cmd doesn't look in the current folder.
		List<String> command = windows ? List.of("cmd", "/c", run.resolve("start.bat").toString())
				: List.of("sh", run.resolve("start.sh").toString());
		Map<String, String> env = Map.of("JAVA", getJavaExecutable().get(),
				"JAVA_OPTS", "-Xmx768m -Doldfaithful.selftest.multiplayer=true");

		List<String> failures = new ArrayList<>();
		List<String> lines;
		try (GameProcess server = new GameProcess(command, run, run.resolve("harness-output.log"), env)) {
			if (server.await(l -> l.contains("Done ("), 300) == null) {
				failures.add("server never finished starting (from start script)");
			} else {
				try {
					scenarios(server, port, failures);
				} catch (Exception e) {
					// e.g. the server stopped answering connections; recorded, so the server is still saved and stopped
					failures.add("scenario stopped: " + e);
					getLogger().lifecycle("Scenario stopped", e);
				}
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
		Path levelDat = world.resolve("level.dat");
		if (!Files.isRegularFile(levelDat) || !gunzipped(levelDat).contains("RandomSeed")) {
			failures.add("world/level.dat is missing or isn't a level file any more");
		} else {
			getLogger().lifecycle("world/level.dat intact (still a level file, with its seed)");
		}
		List<String> playerFiles = new ArrayList<>();
		if (Files.isDirectory(world.resolve("players"))) {
			try (Stream<Path> files = Files.list(world.resolve("players"))) {
				files.forEach(p -> playerFiles.add(p.getFileName().toString()));
			}
		}
		getLogger().lifecycle("Player files: {}", playerFiles);
		for (String f : playerFiles) if (!SAFE_PLAYER_FILE.matcher(f).matches()) failures.add("unexpected player file " + f);
		for (String l : lines) if (l.contains("OutOfMemoryError")) failures.add("log: " + l);

		checkSelfTest(lines, failures);
		for (String p : problems(lines)) failures.add("log: " + p);
		report("Multiplayer test", lines, failures);
	}

	private void scenarios(GameProcess server, int port, List<String> failures) throws Exception {
		// Honest play first, kept connected throughout.
		RawClient honest = new RawClient(port, "Honest");
		String r = honest.login(60);
		if (r != null) {
			failures.add("honest login refused: " + r);
			return;
		}
		walkAndChat(honest, "hello 1", failures);

		// Names that aren't file names: refused before any player exists.
		for (String name : new String[] {"../level", "CON", "a\\b"}) {
			try (RawClient c = new RawClient(port, name)) {
				String reason = c.login(30);
				getLogger().lifecycle("Login as \"{}\": {}", name, reason == null ? "logged in" : "refused, \"" + reason + "\"");
				if (!"Invalid username".equals(reason)) failures.add("login name \"" + name + "\" wasn't refused: " + reason);
			}
		}

		// NaN in a move.
		try (RawClient c = new RawClient(port, "NanMover")) {
			expectLogin(c, failures);
			double[] p = c.position();
			c.move(Double.NaN, p[1], p[2], p[3], 0, 0, true);
			expectKick(c, "Illegal position", "NaN position", failures);
		}

		// Riding: honest motion is fine; ten million blocks in one packet used to hang the server.
		try (RawClient c = new RawClient(port, "Rider1")) {
			expectLogin(c, failures);
			if (server.await(l -> l.contains("Rider1 seated in a boat"), 20) == null) failures.add("Rider1 wasn't seated");
			for (int i = 0; i < 20; i++) {
				c.ridingMove(0.08, -0.05);
				Thread.sleep(50);
			}
			if (c.awaitKick(2) != null) failures.add("honest riding motion was refused");
			else getLogger().lifecycle("Riding with honest motion: still connected");
			c.ridingMove(1e7, 1e7);
			expectKick(c, "Illegal position", "riding motion of 10 million blocks", failures);
		}
		expectResponsive(server, "after the riding motion", failures);

		// Flood: 256 MB of one-byte keep-alives as fast as loopback takes them. Vanilla queues every one (an
		// OutOfMemoryError long before the end at -Xmx768m); now the server stops reading and the sender blocks.
		try (RawClient c = new RawClient(port, "Flooder")) {
			expectLogin(c, failures);
			int from = server.lineCount();
			long total = 256L << 20;
			long written = c.flood(total, 15_000);
			getLogger().lifecycle("Flood: {} KB of {} MB written in 15 s", written >> 10, total >> 20);
			if (written >= total) failures.add("the server read the whole 256 MB flood");
			if (server.awaitFrom(from, l -> l.contains("Read queue full"), 5) == null) failures.add("no read-queue pause logged");
			expectResponsive(server, "during the flood", failures);
		}
		expectResponsive(server, "after the flood", failures);

		movementLoading(server, port, failures);

		if (honest.isClosed()) failures.add("the honest player was disconnected along the way");
		walkAndChat(honest, "hello 2", failures);
		honest.close();
	}

	/**
	 * S6: chunks brought into view by a player's own movement load at a bounded rate. An honest worst case (a minute's
	 * lag at minecart speed arriving at once, scaled here to 20 s) must never be held back; a client sending moves as
	 * fast as vanilla accepts them (about 1,000 blocks a tick) must be, and the server must stay responsive meanwhile.
	 */
	private void movementLoading(GameProcess server, int port, List<String> failures) throws Exception {
		try (RawClient c = new RawClient(port, "SkyBurster")) {
			double[] p = skyLogin(c, failures);
			Thread.sleep(20_000); // the lag: the budget refills meanwhile, as it would for a lagged client
			int from = server.lineCount();
			List<double[]> burst = new ArrayList<>();
			for (int i = 1; i <= 400; i++) burst.add(new double[] {p[0] + 0.4 * i, p[1], p[2] + 0.4 * i});
			c.moveBurst(burst);
			Thread.sleep(3_000);
			boolean held = server.awaitFrom(from, l -> l.contains("SkyBurster is moving faster than chunks may load"), 1) != null;
			getLogger().lifecycle("Honest burst (20 s at minecart speed, diagonally, at once): {}", held ? "held back" : "loaded as vanilla does");
			if (held) failures.add("an honest burst of moves was held back by the chunk loading budget");
			if (c.isClosed()) failures.add("the burst player was disconnected: " + c.awaitKick(1));
		}

		try (RawClient c = new RawClient(port, "SkyRunner")) {
			double[] p = skyLogin(c, failures);
			int from = server.lineCount();
			List<double[]> run = new ArrayList<>();
			for (int i = 1; i <= 2000; i++) run.add(new double[] {p[0] + 9.9 * i, p[1], p[2]});
			c.moveBurst(run);
			expectResponsive(server, "while a client runs 1,000 blocks a tick", failures);
			if (server.awaitFrom(from, l -> l.contains("SkyRunner is moving faster than chunks may load"), 10) == null) {
				failures.add("the runner's chunk loading wasn't bounded");
			}
			// Vanilla accepts every one of those moves (each under its 10-block limit): if it kicked the runner, the
			// moves didn't arrive as meant and the budget wasn't what held the server up.
			if (c.isClosed()) failures.add("the runner was disconnected: " + c.awaitKick(1));
			expectResponsive(server, "after the runner", failures);
		}
	}

	/** Logs in a "Sky" player (the self-test mod places them at y 130, above any terrain); returns {x, feet y, z}. */
	private double[] skyLogin(RawClient c, List<String> failures) throws Exception {
		expectLogin(c, failures);
		double[] p = c.position();
		if (p == null || Math.abs(p[1] - 130) > 0.01) {
			throw new IOException(c.name + " wasn't placed in the sky: " + (p == null ? "no position" : "y " + p[1]));
		}
		return new double[] {p[0], p[1], p[3]};
	}

	private void walkAndChat(RawClient c, String message, List<String> failures) throws Exception {
		double[] p = c.position();
		for (int i = 1; i <= 5; i++) {
			c.move(p[0] + 0.1 * i, p[1], p[2], p[3], 0, 0, true);
			Thread.sleep(50);
		}
		c.chat(message);
		long deadline = System.currentTimeMillis() + 10_000;
		while (!c.sawChat("<" + c.name + "> " + message) && System.currentTimeMillis() < deadline) Thread.sleep(50);
		if (!c.sawChat("<" + c.name + "> " + message)) failures.add(c.name + ": chat \"" + message + "\" didn't come back");
		else getLogger().lifecycle("{} walked and chatted (\"{}\" came back)", c.name, message);
	}

	private void expectLogin(RawClient c, List<String> failures) throws Exception {
		String r = c.login(60);
		if (r != null) failures.add(c.name + " couldn't log in: " + r);
	}

	private void expectKick(RawClient c, String expected, String what, List<String> failures) throws InterruptedException {
		String reason = c.awaitKick(15);
		getLogger().lifecycle("{}: {}", what, reason == null ? "no reaction" : "disconnected, \"" + reason + "\"");
		if (!expected.equals(reason)) failures.add(what + ": expected \"" + expected + "\", got " + reason);
	}

	/** The console's "list" must answer within 5 s: the server's tick loop is alive and not swamped. */
	private void expectResponsive(GameProcess server, String when, List<String> failures) throws Exception {
		int from = server.lineCount();
		long start = System.nanoTime();
		server.send("list");
		String line = server.awaitFrom(from, l -> l.contains("Connected players:"), 5);
		long ms = (System.nanoTime() - start) / 1_000_000;
		if (line == null) failures.add("server unresponsive " + when + " (no answer to \"list\" in 5 s)");
		else getLogger().lifecycle("Server responsive {}: \"list\" answered in {} ms", when, ms);
	}

	private static String gunzipped(Path file) throws IOException {
		try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo(out);
			return out.toString(StandardCharsets.ISO_8859_1);
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket s = new ServerSocket(0)) {
			return s.getLocalPort();
		}
	}
}
