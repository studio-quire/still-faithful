package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Faithful Core's base layer and World Rules, end to end: the server package with them against raw clients
 * (vanilla, with the core, and hostile), the rules' scenes on the real server, the real client (hidden) joining it with the
 * mods and with nothing of the library, a server with a mod that must match, and the real client in single player. Then the lock on both: locked from the console and by the page's
 * button, still locked after a restart or a reopen, and unlocked by taking its line out of the world's file with the game stopped.
 */
public abstract class LibraryRulesTestTask extends ClientStartupTask {
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** The core, settings, the mod and its test-only mod, for the server. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getServerMods();

	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private GameProcess server;

	private void expect(String what, boolean ok) {
		getLogger().lifecycle("{}: {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	private void scene(String command) throws Exception {
		int from = server.lineCount();
		server.send(command);
		String name = command.split(" ")[1];
		if (server.awaitFrom(from, l -> l.contains("[faithful-test] DONE " + name), 60) == null) failures.add("the scene " + command + " didn't finish");
	}

	private boolean answers() throws Exception {
		server.send("list");
		return server.await(l -> l.contains("Connected players"), 15) != null;
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		Path dir = run.resolve("server");
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		for (File mod : getServerMods().getFiles()) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()));
		int port = freePort();
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1",
				"server-port=" + port, "view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=false", "pvp=true", "allow-flight=true", ""));
		Files.writeString(dir.resolve("ops.txt"), "OldFaithfulTest\n");

		start(dir, "plain");
		try {
			rawPeers(port);
			scenes(port);
			hostile(port);
			clientOnServer(run, port);
			plainClientOnServer(run, port);
			lockOnServer(run, dir, port);
		} finally {
			stop("plain");
		}
		Path worldFile = dir.resolve("world").resolve("faithful-world.properties");
		start(dir, "locked");
		try {
			ruleRefused(worldFile, "after a restart");
		} finally {
			stop("locked");
		}
		unlockByHand(worldFile);

		Path matchMod = dir.resolve("mods").resolve("must-match.jar");
		writeMatchMod(matchMod);
		start(dir, "must match");
		try {
			mustMatch(port);
			ruleTaken(worldFile);
		} finally {
			stop("must match");
		}

		Path game = run.resolve("single-player");
		Files.createDirectories(game.resolve("mods"));
		// As a library pack lays it out: the library's mods out of mods/, chosen by the list a launch is told of.
		Path library = game.resolve("mods-library");
		Files.createDirectories(library);
		List<String> selection = new ArrayList<>();
		List<String> serverJars = getServerMods().getFiles().stream().map(File::getName).toList();
		for (File mod : getMods().getFiles()) {
			boolean chosen = serverJars.contains(mod.getName()) && !mod.getName().startsWith("oldfaithful-settings") && !mod.getName().contains("selftest");
			String name = chosen ? mod.getName().replaceFirst("-\\d.*\\.jar$", ".jar") : mod.getName();
			Files.copy(mod.toPath(), (chosen ? library : game.resolve("mods")).resolve(name));
			if (chosen) selection.add("mods-library/" + name);
		}
		Files.write(library.resolve("selection.txt"), selection);
		prepare(game, false);
		lines.add("--- single player");
		lines.addAll(launch(game, false, List.of("-Dfabric.addMods=@mods-library/selection.txt", "-Doldfaithful.selftest.recordText=true"), failures));
		// The first launch locked the world by the page's button.
		lines.add("--- single player, reopened locked, with a rule a mod brought since");
		// Off the list, so never loaded: pages for the switches. The self-test mod, which has the late rule, in the folder and depending on one with no rules.
		writeListing(library.resolve("side-door-base.jar"), "side-door-base", "Side Door Base", "");
		writeListing(library.resolve("world-rules-selftest.jar"), "world-rules-selftest", "World Rules self-test", ",\"depends\":{\"side-door-base\":\"*\"}");
		writeListing(library.resolve("free-mod.jar"), "free-mod", "Free Mod", "");
		lines.addAll(launch(game, false, List.of("-Dfabric.addMods=@mods-library/selection.txt", "-Dworldrules.test.phase=reopened", "-Dworldrules.test.lateRule=true",
				"-Doldfaithful.selftest.recordText=true"), failures));
		unlockByHand(game.resolve("saves").resolve("oldfaithful-startup").resolve("faithful-world.properties"));
		lines.add("--- single player, unlocked by hand");
		lines.addAll(launch(game, false, List.of("-Dfabric.addMods=@mods-library/selection.txt", "-Dworldrules.test.phase=unlocked"), failures));
		report("Library rules test", lines, failures);
	}

	private void start(Path dir, String label) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		server = new GameProcess(command, dir, dir.resolve("harness-output-" + label.replace(' ', '-') + ".log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m"));
		if (server.await(l -> l.contains("Done ("), 300) == null) {
			server.close();
			throw new IOException("the server never finished starting: " + dir);
		}
	}

	private void stop(String label) throws Exception {
		server.send("stop");
		Integer exit = server.waitFor(120);
		if (exit == null) failures.add(label + ": the server didn't stop within 2 minutes");
		List<String> out = server.lines();
		server.close();
		for (String line : out) {
			if (line.contains("[faithful-test] FAIL")) failures.add(line);
			if (line.contains("[faithful-test] PASS")) getLogger().lifecycle(line);
		}
		for (String p : problems(out)) failures.add(label + ", server: " + p);
		lines.add("--- server: " + label);
		lines.addAll(out);
	}

	private void rawPeers(int port) throws Exception {
		try (RawClient vanilla = new RawClient(port, "PlainPlayer"); RawClient core = new RawClient(port, "CorePlayer").core(true)) {
			expect("a vanilla client logs in", vanilla.login(60) == null);
			vanilla.stand(20, 50);
			expect("a vanilla client is sent no 250 packet", vanilla.await(r -> r.id() == 250, 1) == null);
			expect("a client with the core logs in", core.login(60) == null);
			expect("its hello lists the rules channel", core.helloChannels() != null && core.helloChannels().containsValue("faithful-core:rules"));
			expect("nothing here must match", !core.serverMustMatch());
			expect("it's told the world's rules", core.await(r -> r.id() == 250 && r.a() != 0, 5) != null);
			expect("the vanilla client is still in", !vanilla.isClosed());
		}
	}

	private void scenes(int port) throws Exception {
		try (RawClient p = new RawClient(port, "RuleTester")) {
			expect("the scenes' player logs in", p.login(60) == null);
			p.stand(20, 50);
			scene("ruletest explosions");
			scene("ruletest bed RuleTester");
			scene("ruletest night");
			Thread.sleep(500);
			scene("ruletest bednight RuleTester");
			server.send("rule keep_inventory true");
			Thread.sleep(500);
			scene("ruletest kill RuleTester true");
			p.respawn();
			p.stand(20, 50);
			expect("a vanilla client is sent its kept things: " + p.inventory(), p.inventory().contains(264) && p.inventory().contains(306));
			scene("ruletest kept RuleTester true");
			server.send("rule keep_inventory false");
			Thread.sleep(500);
			scene("ruletest kill RuleTester false");
			p.respawn();
			p.stand(20, 50);
			scene("ruletest kept RuleTester false");
			monsters(p);
		}
	}

	/** Natural spawning with the rule on and off, in each dimension, each with a player there as it needs. */
	private void monsters(RawClient p) throws Exception {
		for (int dimension : new int[] {0, -1}) {
			if (dimension == -1) {
				scene("ruletest nether RuleTester");
				p.stand(20, 50);
			}
			for (boolean on : new boolean[] {true, false}) {
				server.send("rule natural_monsters " + on);
				scene("ruletest spawning " + dimension);
				// Beta fills its quota of monsters in a tick or two, and a raw player can't run from them.
				p.stand(on ? 30 : 150, 100);
				expect("the scenes' player is still in", !p.isClosed());
				scene("ruletest spawned " + dimension + " " + on);
			}
		}
		server.send("rule natural_monsters true");
		scene("ruletest peaceful");
		scene("ruletest nether RuleTester");
		p.stand(20, 50);
	}

	private void hostile(int port) throws Exception {
		try (RawClient honest = new RawClient(port, "Honest")) {
			expect("the honest player logs in", honest.login(60) == null);
			try (RawClient bad = new RawClient(port, "BadPrefix").core(true)) {
				bad.login(60);
				bad.sendChannel(0x12345678, 1, 0, new byte[0]);
				bad.stand(10, 50);
				expect("a 250 with a stranger's prefix ends that connection", bad.isClosed());
			}
			try (RawClient bad = new RawClient(port, "BadLength").core(true)) {
				bad.login(60);
				bad.sendChannel(RawClient.CORE_PREFIX, 1, Integer.MAX_VALUE, new byte[0]);
				bad.stand(10, 50);
				expect("a 2 GB message ends that connection without allocating it", bad.isClosed());
			}
			try (RawClient bad = new RawClient(port, "BadNegative").core(true)) {
				bad.login(60);
				bad.sendChannel(RawClient.CORE_PREFIX, 1, -1, new byte[0]);
				bad.stand(10, 50);
				expect("a negative length ends that connection", bad.isClosed());
			}
			try (RawClient odd = new RawClient(port, "OddChannels").core(true)) {
				odd.login(60);
				odd.sendChannel(RawClient.CORE_PREFIX, 9999, 3, new byte[3]);
				odd.sendChannel(RawClient.CORE_PREFIX, 0, 3, new byte[3]);
				odd.stand(10, 50);
				expect("an unknown channel, and a hello after login, are ignored", !odd.isClosed());
			}
			try (RawClient bad = new RawClient(port, "VanillaBad")) {
				bad.login(60);
				bad.sendChannel(RawClient.CORE_PREFIX, 1, 3, new byte[3]);
				bad.stand(10, 50);
				expect("a client that never declared itself is ignored", !bad.isClosed());
			}
			expect("the server still answers", answers());
			expect("the honest player is still in", !honest.isClosed());
		}
	}

	private void clientOnServer(Path run, int port) throws Exception {
		server.send("rule keep_inventory true");
		server.send("rule sleeping false");
		server.send("rule weather rain");
		Thread.sleep(500);
		Path game = run.resolve("client-on-server");
		Files.createDirectories(game.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		lines.add("--- client on the server");
		lines.addAll(launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true",
				"-Dfaithful.test.keep=true"), failures));
		server.send("rule keep_inventory false");
		server.send("rule sleeping true");
	}

	/** Nothing of the library on the client, on a server holding rain by its rule: it must play, in that rain. */
	private void plainClientOnServer(Path run, int port) throws Exception {
		server.send("rule weather rain");
		Path game = run.resolve("plain-client-on-server");
		Files.createDirectories(game.resolve("mods"));
		List<String> serverJars = getServerMods().getFiles().stream().map(File::getName).toList();
		for (File mod : getMods().getFiles()) {
			// More Faithful's own jar is a client's only, and needs Faithful Settings.
			boolean library = serverJars.contains(mod.getName()) || mod.getName().startsWith("morefaithful-presets");
			if (!library) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		}
		prepare(game, false);
		lines.add("--- plain client on the server");
		lines.addAll(launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true",
				"-Doldfaithful.selftest.expectRain=true"), failures));
		server.send("rule weather normal");
	}

	/** What the server said to a console command, from its output. */
	private List<String> console(String command) throws Exception {
		int from = server.lineCount();
		server.send(command);
		server.send("list");
		server.awaitFrom(from, l -> l.contains("Connected players"), 15);
		List<String> out = server.lines();
		return new ArrayList<>(out.subList(Math.min(from, out.size()), out.size()));
	}

	private static boolean says(List<String> said, String text) {
		return said.stream().anyMatch(l -> l.contains(text));
	}

	/** Locked from the console, asked once first; then /rule refused from the console and from a client, which sees it locked. */
	private void lockOnServer(Path run, Path dir, int port) throws Exception {
		Path file = dir.resolve("world").resolve("faithful-world.properties");
		List<String> said = console("rule lock");
		expect("rule lock asks to be sure, saying it can't be undone in game and nothing of how to unlock: " + said, says(said, "can't be undone") && !says(said, "rules-locked")
				&& says(said, "rule lock confirm"));
		expect("and locks nothing yet", !Files.readString(file).contains("rules-locked"));
		said = console("rule lock confirm");
		expect("rule lock confirm locks, in the world's file: " + said, says(said, "rules are locked") && !says(said, "rules-locked") && Files.readString(file).contains("rules-locked=true"));
		ruleRefused(file, "from the console");
		said = console("rule");
		expect("the rules can still be read, and say they're locked: " + said, says(said, "rules are locked") && says(said, "world-rules:keep_inventory = false"));
		said = console("rule lock");
		expect("locking again says it's already locked", says(said, "already locked") && !says(said, "rules-locked"));
		try (RawClient core = new RawClient(port, "LockedCore").core(true)) {
			expect("a client with the core still logs in to a locked server", core.login(60) == null);
		}
		String before = Files.readString(file);
		Path game = run.resolve("client-on-locked-server");
		Files.createDirectories(game.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		// World Rules is loaded from mods/; this gives its page the library's switch.
		writeListing(Files.createDirectories(game.resolve("mods-library")).resolve("world-rules.jar"), "world-rules", "World Rules", "");
		prepare(game, false);
		lines.add("--- client on the locked server");
		lines.addAll(launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true",
				"-Dworldrules.test.serverLocked=true", "-Doldfaithful.selftest.recordText=true"), failures));
		expect("an op's /rule from the client reached the server", server.lines().stream().anyMatch(l -> l.contains("OldFaithfulTest issued server command: rule keep_inventory true")));
		expect("and changed nothing", Files.readString(file).equals(before));
	}

	/** {@code /rule} refused from the console, saying nothing of how to unlock, with the world's file as it was. */
	private void ruleRefused(Path file, String when) throws Exception {
		String before = Files.readString(file);
		List<String> said = console("rule keep_inventory true");
		expect("locked, " + when + ": the console's /rule is refused, saying nothing of how to unlock: " + said, says(said, "rules are locked") && !says(said, "rules-locked")
				&& !says(said, "keep_inventory = true"));
		expect("locked, " + when + ": the world's file as it was", Files.readString(file).equals(before));
	}

	/** As a player would: the line taken out with the game stopped, the rest of the file left. */
	private void unlockByHand(Path file) throws IOException {
		List<String> kept = new ArrayList<>(Files.readAllLines(file));
		boolean had = kept.remove("rules-locked=true");
		expect("the world's file had the lock's line to take out", had);
		Files.write(file, kept);
	}

	private void ruleTaken(Path file) throws Exception {
		List<String> said = console("rule keep_inventory true");
		expect("unlocked by hand: /rule changes a rule again: " + said, says(said, "keep_inventory = true") && Files.readString(file).contains("rule.world-rules.keep_inventory=true"));
		console("rule keep_inventory false");
	}

	/** A jar for the library's folder with only what the switches read; {@code more}: the rest of its json, from a comma. */
	private static void writeListing(Path jar, String id, String name, String more) throws IOException {
		try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1\",\"name\":\"" + name + "\"" + more + "}").getBytes("UTF-8"));
			zip.closeEntry();
		}
	}

	private static void writeMatchMod(Path jar) throws IOException {
		try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write("{\"schemaVersion\":1,\"id\":\"must-match\",\"version\":\"1\",\"custom\":{\"faithful\":{\"match\":true}}}".getBytes("UTF-8"));
			zip.closeEntry();
		}
	}

	private void mustMatch(int port) throws Exception {
		try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
			String reason = vanilla.login(60);
			expect("a vanilla client is refused, plainly: " + reason, reason != null && reason.contains("Faithful Library"));
		}
		try (RawClient core = new RawClient(port, "Agrees").core(true)) {
			expect("a client that agrees gets in", core.login(60) == null);
			expect("its hello says something must match", core.serverMustMatch());
		}
		try (RawClient core = new RawClient(port, "Disagrees").core(false)) {
			String reason = core.login(60);
			expect("a client that doesn't agree is let go: " + reason, reason != null);
		}
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else Files.copy(p, target, StandardCopyOption.COPY_ATTRIBUTES);
			}
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
