package oldfaithful.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import oldfaithful.gradle.LibraryTestTask.Stage;

/**
 * Mods' own tests (docs/library-authoring.md > Testing): all of the set's, one after another in one world, so each also meets the others. Single player, then a real server with a hidden client
 * joined, each fresh and again after a save and a restart. A FAIL from a mod's test, on either side, fails the run under its name, and so does a mod with a test that checked nothing.
 */
final class LibrarySelftestStage implements Stage {
	private final LibraryTestTask t;
	private final List<String> joinFlags = new ArrayList<>();

	LibrarySelftestStage(LibraryTestTask t) {
		this.t = t;
	}

	@Override
	public void run() throws Exception {
		List<LibraryMod> tests = new ArrayList<>();
		for (File jar : t.getSelftests().getFiles()) {
			LibraryMod test = LibraryMod.read(jar.toPath());
			String owner = test.id().replaceFirst("-selftest$", "");
			if (test.tests() && t.subjects.stream().anyMatch(m -> m.id().equals(owner))) tests.add(test);
		}
		List<String> mods = new ArrayList<>();
		tests.forEach(m -> mods.add(m.id().replaceFirst("-selftest$", "")));
		t.subjects.stream().filter(LibraryMod::tests).forEach(m -> mods.add(m.id()));
		if (mods.isEmpty()) {
			t.notes.add("selftest: no mod in the set has one");
			return;
		}

		List<Path> client = t.clientMods(t.subjects, true), joined = t.clientMods(t.subjects, true), server = t.serverMods(t.subjects, true);
		List<String> onServer = new ArrayList<>(mods);
		for (LibraryMod m : tests) {
			client.add(m.jar());
			if (!m.client()) server.add(m.jar());
			if (m.client() && m.content()) {
				// Content has to match, and a server can't load a client-only jar: such a test can only be single player's.
				onServer.remove(m.id().replaceFirst("-selftest$", ""));
				t.notes.add(m.id() + ": selftest: client-only with content, so it's left out of runs on a server");
			} else {
				joined.add(m.jar());
			}
		}
		Path dir = t.run.resolve(t.stage);
		String planted = LibraryTestTask.flag("faithful.bad", t.planted);
		if (t.getServerOnly().get()) {
			serverOnly(tests, server, dir, planted);
			return;
		}
		// Mods run one after another, so a launch's time goes by how many: what a slow one takes, with room to start and stop. The test stops itself a little before the task would stop it.
		t.longLaunch = 120 + 100 * mods.size();
		String stall = LibraryTestTask.flag("oldfaithful.selftest.stallMs", (t.longLaunch - 20) * 1000L);
		// So a refusal can say what it was told.
		joinFlags.add("-Doldfaithful.selftest.recordText=true");

		Path game = dir.resolve("single-player");
		Files.createDirectories(game);
		t.testsPassed.clear();
		t.client("single player", game, client, List.of("-Doldfaithful.selftest.worldChecksOnly=true", "-Doldfaithful.selftest.recordText=true", "-Dfaithful.test=first", stall, planted));
		keepLog(game, "first");
		keepSave(game);
		mustHaveChecked(mods, "single player");
		counted("single player");
		t.client("single player, restarted", game, client, List.of("-Doldfaithful.selftest.worldChecksOnly=true", "-Doldfaithful.selftest.recordText=true", "-Dfaithful.test=restarted", stall, planted));
		keepLog(game, "restarted");
		counted("single player, restarted");

		try (HarnessServer host = new HarnessServer(t, dir.resolve("server"))) {
			host.mods(server).properties(Map.of("spawn-animals", "true", "level-seed", String.valueOf(t.getSeed().get())));
			t.testsPassed.clear();
			if (join(host, joined, dir.resolve("joined"), "first", "on a server", stall, planted)) {
				mustHaveChecked(onServer, "on a server");
				counted("on a server");
				join(host, joined, dir.resolve("joined"), "restarted", "on a server, restarted", stall, planted);
				counted("on a server, restarted");
			}
			if (t.subjects.stream().anyMatch(LibraryMod::content)) serversWithout(dir.resolve("without"), joined, stall, planted);
		} finally {
			t.longLaunch = 0;
		}
	}

	/**
	 * A client with the set's content on servers that have none of it, which admit it (docs/library.md > What must match): one with the core alone, then the real one. Each mod's
	 * {@code faithful:test-without} runs there, as an op that can {@code /give} itself what it needs.
	 */
	private void serversWithout(Path dir, List<Path> jars, String stall, String planted) throws Exception {
		try (HarnessServer core = new HarnessServer(t, dir.resolve("core-server"))) {
			core.mods(t.serverMods(List.of(), false)).properties(Map.of("level-seed", String.valueOf(t.getSeed().get())));
			if (core.start(List.of())) joinWithout("on a server with only the core", dir.resolve("core-client"), jars, core.port, stall, planted);
			else t.fail("harness", "failed-to-start", "a server with only the core");
			t.scan("server: only the core", core.stop(null));
		}
		Path plain = dir.resolve("real-server");
		Files.createDirectories(plain);
		Files.copy(t.getServerJar().get().getAsFile().toPath(), plain.resolve("minecraft_server.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(plain.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=3", "spawn-monsters=false", "spawn-animals=false", "level-seed=" + t.getSeed().get(), ""));
		Files.writeString(plain.resolve("ops.txt"), "OldFaithfulTest\n");
		try (GameProcess real = new GameProcess(List.of(t.getJavaExecutable().get(), "-Xmx512m", "-jar", "minecraft_server.jar", "nogui"), plain, plain.resolve("harness-output.log"), Map.of())) {
			if (real.await(l -> l.contains("Done ("), 120) == null) {
				t.fail("harness", "failed-to-start", "the real server");
				return;
			}
			joinWithout("on the real server", dir.resolve("real-client"), jars, port, stall, planted);
			real.send("stop");
			real.waitFor(60);
		}
	}

	private void joinWithout(String where, Path game, List<Path> jars, int port, String stall, String planted) throws Exception {
		Files.createDirectories(game);
		t.testsPassed.clear();
		t.client(where, game, jars, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true", "-Dfaithful.test=without", stall, planted));
		counted(where);
	}

	/** A launch's log is overwritten by the next in that folder, and a pass is only worth what can be read back. */
	private static void keepLog(Path game, String name) throws java.io.IOException {
		Files.copy(game.resolve("harness-output.log"), game.resolve("harness-output-" + name + ".log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	/** The second life rewrites the world, and what a restart lost is read from what the first life left: {@code saves-first}. */
	private static void keepSave(Path game) throws java.io.IOException {
		Path saves = game.resolve("saves"), kept = game.resolve("saves-first");
		if (!Files.isDirectory(saves)) return;
		try (java.util.stream.Stream<Path> all = Files.walk(saves)) {
			for (Path from : (Iterable<Path>) all::iterator) {
				Path to = kept.resolve(saves.relativize(from).toString());
				if (Files.isDirectory(from)) Files.createDirectories(to);
				else Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private void counted(String where) {
		t.getLogger().lifecycle("Checks passed, {}: {}", where, new TreeMap<>(t.testsPassed));
		t.testsPassed.clear();
	}

	/** World tests with no game client at all: light enough for a PC that's in use, though a {@code ClientTest} or a test's server commands don't run. */
	private void serverOnly(List<LibraryMod> tests, List<Path> server, Path dir, String planted) throws Exception {
		List<String> mods = new ArrayList<>();
		tests.stream().filter(LibraryMod::worldTests).forEach(m -> mods.add(m.id().replaceFirst("-selftest$", "")));
		t.subjects.stream().filter(LibraryMod::worldTests).forEach(m -> mods.add(m.id()));
		java.util.Collections.sort(mods);
		t.getLogger().lifecycle("World tests, on a server with no game client: {}", mods);
		try (HarnessServer host = new HarnessServer(t, dir.resolve("server"))) {
			// A raw client stands where it's put, with nothing keeping it on the floor.
			host.mods(server).properties(Map.of("spawn-animals", "true", "level-seed", String.valueOf(t.getSeed().get()), "allow-flight", "true"));
			t.testsPassed.clear();
			if (rawRun(host, mods, false, "on a server", planted)) {
				mustHaveChecked(mods, "on a server");
				counted("on a server");
				rawRun(host, mods, true, "on a server, restarted", planted);
				counted("on a server, restarted");
			}
		}
	}

	private boolean rawRun(HarnessServer host, List<String> mods, boolean restarted, String where, String planted) throws Exception {
		boolean up = host.start(List.of("-Dfaithful.test=host", planted));
		if (up) {
			try (RawClient player = new RawClient(host.port, "OldFaithfulTest").core(true)) {
				String refused = player.login(30);
				if (refused != null) {
					t.fail("harness", "refused", refused + " [" + where + "]");
				} else {
					player.stand(5, 100);
					int channel = testChannel(player);
					for (int plot = 0; plot < mods.size() && channel >= 0; plot++) {
						int from = host.lineCount();
						byte[] message = runMessage(mods.get(plot), plot, restarted);
						player.sendChannel(RawClient.CORE_PREFIX, channel, message.length, message);
						long deadline = System.currentTimeMillis() + 300_000;
						boolean done = false;
						while (!done && System.currentTimeMillis() < deadline) {
							// A player has to answer the server's moves, or it holds them where they were.
							player.stand(1, 50);
							player.keepAlive();
							done = host.awaitLine(from, "[library-harness] DONE test " + mods.get(plot), 1);
						}
						if (!done) t.fail(mods.get(plot), "selftest", "its world test didn't end within five minutes [" + where + "]");
					}
					if (channel >= 0 && !mods.isEmpty()) {
						byte[] end = endMessage(mods.get(mods.size() - 1));
						player.sendChannel(RawClient.CORE_PREFIX, channel, end.length, end);
						player.stand(5, 100);
					}
				}
			}
			if (!host.save()) t.fail("harness", "save", "save-all didn't finish [" + where + "]");
		}
		Integer[] exit = {null};
		t.scan("server: " + where, host.stop(exit));
		if (!up) t.fail("harness", "failed-to-start", "the server didn't start [" + where + "]");
		else if (exit[0] == null) t.fail("harness", "stop", "the server didn't stop within two minutes [" + where + "]");
		return up;
	}

	private int testChannel(RawClient player) {
		for (Map.Entry<Integer, String> c : player.helloChannels().entrySet()) {
			if (c.getValue().equals("harness-probe:test")) return c.getKey();
		}
		t.fail("harness", "selftest", "the server's hello has no harness-probe:test");
		return -1;
	}

	/** TestHost's OP_END. */
	private static byte[] endMessage(String mod) throws java.io.IOException {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
			out.writeByte(3);
			out.writeUTF(mod);
		}
		return bytes.toByteArray();
	}

	/** TestHost's OP_RUN. */
	private static byte[] runMessage(String mod, int plot, boolean restarted) throws java.io.IOException {
		java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
			out.writeByte(1);
			out.writeUTF(mod);
			out.writeInt(plot);
			out.writeBoolean(restarted);
		}
		return bytes.toByteArray();
	}

	private boolean join(HarnessServer host, List<Path> jars, Path game, String mode, String where, String stall, String planted) throws Exception {
		Files.createDirectories(game);
		boolean up = host.start(List.of("-Dfaithful.test=host", planted));
		if (up) {
			List<String> jvm = new ArrayList<>(List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + host.port, "-Doldfaithful.selftest.joinWorldChecks=true", "-Dfaithful.test=" + mode, stall, planted));
			jvm.addAll(joinFlags);
			t.client(where, game, jars, jvm);
			keepLog(game, mode);
			if (!host.save()) t.fail("harness", "save", "save-all didn't finish [" + where + "]");
		}
		Integer[] exit = {null};
		t.scan("server: " + where, host.stop(exit));
		if (!up) t.fail("harness", "failed-to-start", "the server didn't start [" + where + "]");
		else if (exit[0] == null) t.fail("harness", "stop", "the server didn't stop within two minutes [" + where + "]");
		return up;
	}

	private void mustHaveChecked(List<String> mods, String where) {
		for (String mod : mods) {
			if (t.testsPassed.getOrDefault(mod, 0) == 0 && t.failures.stream().noneMatch(f -> f.mod().equals(mod) && f.rule().equals("selftest"))) {
				t.fail(mod, "selftest-no-checks", "it has a test, and it reported no checks [" + where + "]");
			}
		}
	}
}
