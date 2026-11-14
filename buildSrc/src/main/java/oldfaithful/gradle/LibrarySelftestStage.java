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
 * Two halves can run alone, for iterating on one side: {@code serverOnly} (world tests on a server, a raw client for a player, no game client) and
 * {@code singlePlayerOnly} (single player, fresh and reopened, no server).
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
		// On an edition's mods too (-Plibrary.edition=old or still), for what only shows with them: Still Faithful's lighting and the like.
		String edition = t.getSelftestEdition().get();
		List<String> editionFlags = new ArrayList<>();
		if (!edition.isEmpty()) {
			List<Path> extra = new ArrayList<>();
			t.getOldMods().getFiles().forEach(f -> extra.add(f.toPath()));
			if (edition.equals("still")) {
				t.getStillMods().getFiles().forEach(f -> extra.add(f.toPath()));
				if (!t.getStillEdition().get().isEmpty()) editionFlags.add(LibraryTestTask.flag("oldfaithful.selftest.edition", t.getStillEdition().get()));
			} else if (!edition.equals("old")) {
				t.fail("harness", "options", "library.edition is old or still, not " + edition);
				return;
			}
			client.addAll(extra);
			joined.addAll(extra);
			server.addAll(extra);
			// The joining client has the edition's mods too, so its title check expects the edition's name.
			joinFlags.addAll(editionFlags);
			t.notes.add("selftest: on " + edition + " edition's mods as well (library.edition)");
		}
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
		if (t.getServerOnly().get() && t.getSinglePlayerOnly().get()) {
			t.fail("harness", "options", "library.serverOnly and library.singlePlayerOnly each leave out what the other runs: pick one");
			return;
		}
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
		t.client("single player", game, client, with(List.of("-Doldfaithful.selftest.worldChecksOnly=true", "-Doldfaithful.selftest.recordText=true", "-Dfaithful.test=first", stall, planted), editionFlags));
		keepLog(game, "first");
		keepSave(game);
		mustHaveChecked(mods, "single player");
		counted("single player");
		t.client("single player, restarted", game, client, with(List.of("-Doldfaithful.selftest.worldChecksOnly=true", "-Doldfaithful.selftest.recordText=true", "-Dfaithful.test=restarted", stall, planted), editionFlags));
		keepLog(game, "restarted");
		counted("single player, restarted");
		if (t.getSinglePlayerOnly().get()) {
			t.notes.add("selftest: single player only (library.singlePlayerOnly): no server joined, no servers without the set");
			t.longLaunch = 0;
			return;
		}

		// Launches overlap where one would only wait for the other (2026-11-10: Alchemy's stage 72 s to 56): a client starts beside its
		// server, or while the server restarts, and is let in when the server says it's done (LibraryTestTask.Joining); and each server without the
		// set starts while the client before it plays, so it's ready when its own client is. Never two game clients at once.
		// Not let in sooner, though the port is open before the world loads: a login would wait through the loading against the client's
		// own limits (a minute without a packet, 90 s to get into the world), where a server has three minutes to start.
		boolean without = t.subjects.stream().anyMatch(LibraryMod::content);
		Path guest = dir.resolve("joined");
		List<String> hostJvm = List.of("-Dfaithful.test=host", planted);
		try (HarnessServer host = new HarnessServer(t, dir.resolve("server")); HarnessServer core = without ? new HarnessServer(t, dir.resolve("without").resolve("core-server")) : null) {
			host.mods(server).properties(Map.of("spawn-animals", "true", "level-seed", String.valueOf(t.getSeed().get())));
			if (without) core.mods(t.serverMods(List.of(), false)).properties(Map.of("level-seed", String.valueOf(t.getSeed().get())));
			t.testsPassed.clear();
			host.launch(hostJvm);
			boolean up;
			try (LibraryTestTask.Joining first = t.joining("on a server", guest, joined, host.port, joinJvm("first", stall, planted))) {
				up = letIn(host, first);
			}
			if (up) keepLog(guest, "first");
			else if (without) core.launch(List.of());
			if (up) {
				// This client waits at its menu while the server saves, stops and starts again.
				try (LibraryTestTask.Joining second = t.joining("on a server, restarted", guest, joined, host.port, joinJvm("restarted", stall, planted))) {
					stopHost(host, true, "on a server");
					mustHaveChecked(onServer, "on a server");
					counted("on a server");
					host.launch(hostJvm);
					up = host.awaitStart();
					if (up) second.admit();
					// Once this one is up, not beside it: three JVMs starting together each took about half as long again.
					if (without) core.launch(List.of());
					if (up) second.await();
				}
				if (up) keepLog(guest, "restarted");
				stopHost(host, up, "on a server, restarted");
				counted("on a server, restarted");
			} else {
				stopHost(host, false, "on a server");
			}
			if (without) serversWithout(dir.resolve("without"), core, joined, stall, planted);
		} finally {
			t.longLaunch = 0;
		}
	}

	private List<String> joinJvm(String mode, String stall, String planted) {
		List<String> jvm = new ArrayList<>(List.of("-Doldfaithful.selftest.joinWorldChecks=true", "-Dfaithful.test=" + mode, stall, planted));
		jvm.addAll(joinFlags);
		return jvm;
	}

	/** Lets the client in once the server is ready, and waits for it to play its tests through; false, the client ended, if the server didn't come up. */
	private static boolean letIn(HarnessServer server, LibraryTestTask.Joining client) throws Exception {
		if (!server.awaitStart()) return false;
		client.admit();
		client.await();
		return true;
	}

	private void stopHost(HarnessServer host, boolean up, String where) throws Exception {
		if (up && !host.save()) t.fail("harness", "save", "save-all didn't finish [" + where + "]");
		Integer[] exit = {null};
		t.scan("server: " + where, host.stop(exit));
		if (!up) t.fail("harness", "failed-to-start", "the server didn't start [" + where + "]");
		else if (exit[0] == null) t.fail("harness", "stop", "the server didn't stop within two minutes [" + where + "]");
	}

	/**
	 * A client with the set's content on servers that have none of it, which admit it: one with the core alone ({@code core}, started
	 * already), then the real one. Each mod's {@code faithful:test-without} runs there, as an op that can {@code /give} itself what it needs.
	 */
	private void serversWithout(Path dir, HarnessServer core, List<Path> jars, String stall, String planted) throws Exception {
		Path plain = dir.resolve("real-server");
		Files.createDirectories(plain);
		Files.copy(t.getServerJar().get().getAsFile().toPath(), plain.resolve("minecraft_server.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(plain.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=3", "spawn-monsters=false", "spawn-animals=false", "level-seed=" + t.getSeed().get(), ""));
		Files.writeString(plain.resolve("ops.txt"), "OldFaithfulTest\n");
		List<String> jvm = List.of("-Doldfaithful.selftest.joinWorldChecks=true", "-Dfaithful.test=without", stall, planted);
		// The real server starts as the core's client does: nine seconds of making its world, over by the time that client has played.
		try (GameProcess real = new GameProcess(List.of(t.getJavaExecutable().get(), "-Xmx512m", "-jar", "minecraft_server.jar", "nogui"), plain, plain.resolve("harness-output.log"), Map.of())) {
			t.testsPassed.clear();
			try (LibraryTestTask.Joining client = t.joining("on a server with only the core", dir.resolve("core-client"), jars, core.port, jvm)) {
				if (letIn(core, client)) counted("on a server with only the core");
				else t.fail("harness", "failed-to-start", "a server with only the core");
			}
			t.scan("server: only the core", core.stop(null));

			t.testsPassed.clear();
			try (LibraryTestTask.Joining client = t.joining("on the real server", dir.resolve("real-client"), jars, port, jvm)) {
				if (real.await(l -> l.contains("Done ("), 120) == null) {
					t.fail("harness", "failed-to-start", "the real server");
					return;
				}
				client.admit();
				client.await();
				counted("on the real server");
			}
			real.send("stop");
			real.waitFor(60);
		}
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

	private static List<String> with(List<String> a, List<String> b) {
		List<String> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	private void mustHaveChecked(List<String> mods, String where) {
		for (String mod : mods) {
			if (t.testsPassed.getOrDefault(mod, 0) == 0 && t.failures.stream().noneMatch(f -> f.mod().equals(mod) && f.rule().equals("selftest"))) {
				t.fail(mod, "selftest-no-checks", "it has a test, and it reported no checks [" + where + "]");
			}
		}
	}
}
