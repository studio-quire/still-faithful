package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
 * Faithful Core's content layer end to end (docs/library.md): a server with a content mod, its blocks reloaded under a different
 * numbering, a real client seeing them through every packet, a vanilla client refused, the mod removed and put back, and the world
 * opened by the real, unmodified server. Then single player.
 */
public abstract class LibraryContentTestTask extends ClientStartupTask {
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** The core, settings and the content mod, for the server. */
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
		server.send(command);
		String name = command.split(" ")[1];
		if (server.await(l -> l.contains("[faithful-test] DONE " + name), 60) == null) failures.add("the scene " + command + " didn't finish");
	}

	private void start(Path dir, String label, String jvm) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		server = new GameProcess(command, dir, dir.resolve("harness-output-" + label + ".log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m " + jvm));
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
				"server-port=" + port, "view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=false", "pvp=true",
				// The Nether's copy of the world data moves the spawn after a first start, and the scenes go by the spawn.
				"allow-nether=false", ""));
		Files.writeString(dir.resolve("ops.txt"), "OldFaithfulTest\n");

		start(dir, "first", "");
		try {
			scene("contest ids");
			scene("contest stack");
			scene("contest stash");
			scene("contest place");
			try (RawClient vanilla = new RawClient(port, "PlainPlayer")) {
				String reason = vanilla.login(60);
				expect("a vanilla client is refused, plainly: " + reason, reason != null && reason.contains("Faithful Library"));
			}
			server.send("save-all");
			server.await(l -> l.contains("Save complete."), 60);
		} finally {
			stop("first");
		}

		start(dir, "salted", "-Dfaithful.test.numberSalt=shifted");
		try {
			scene("contest ids");
			scene("contest verify");
			scene("contest stashed");
		} finally {
			stop("salted");
		}

		start(dir, "again", "");
		try {
			Path game = run.resolve("client-on-server");
			Files.createDirectories(game.resolve("mods"));
			for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
			prepare(game, false);
			lines.add("--- client on the server");
			lines.addAll(launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true"), failures));
		} finally {
			stop("again");
		}

		Path modJar = null;
		try (Stream<Path> mods = Files.list(dir.resolve("mods"))) {
			modJar = mods.filter(p -> p.getFileName().toString().startsWith("content-check")).findFirst().orElseThrow();
		}
		Path aside = run.resolve("content-check.jar");
		Files.move(modJar, aside);
		Path worldFile = dir.resolve("world").resolve("faithful-world.properties");
		expect("the world's file notes the mod", Files.readString(worldFile).contains("mod.content-check="));
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		try (GameProcess refused = new GameProcess(command, dir, dir.resolve("harness-output-refused.log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx768m"))) {
			String line = refused.await(l -> l.contains("accept-missing-content") || l.contains("Done ("), 120);
			expect("without the mod, a server stops and says how to go on: " + line, line != null && line.contains("accept-missing-content"));
			refused.send("stop");
			refused.waitFor(30);
		}
		Files.writeString(worldFile, Files.readString(worldFile) + "accept-missing-content=true\n");
		start(dir, "removed", "");
		server.send("save-all");
		server.await(l -> l.contains("Save complete."), 60);
		stop("removed");

		Files.move(aside, modJar);
		start(dir, "returned", "");
		try {
			scene("contest verify");
		} finally {
			stop("returned");
		}

		// The real, unmodified server on a copy: it must load, run and save.
		Path plain = run.resolve("vanilla-server");
		Files.createDirectories(plain);
		copyTree(dir.resolve("world"), plain.resolve("world"));
		Files.copy(getServerJar().get().getAsFile().toPath(), plain.resolve("minecraft_server.jar"));
		Files.writeString(plain.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + freePort(), "view-distance=3", ""));
		try (GameProcess vanilla = new GameProcess(List.of(getJavaExecutable().get(), "-Xmx512m", "-jar", "minecraft_server.jar", "nogui"), plain, plain.resolve("harness-output.log"), Map.of())) {
			expect("the real server opens the world", vanilla.await(l -> l.contains("Done ("), 120) != null);
			vanilla.send("save-all");
			expect("and saves it", vanilla.await(l -> l.contains("Save complete."), 60) != null);
			vanilla.send("stop");
			expect("and stops", vanilla.waitFor(60) != null);
			for (String l : vanilla.lines()) {
				if ((l.contains("Exception") || l.contains("SEVERE")) && !l.contains("Failed to load")) failures.add("vanilla server: " + l);
			}
		}

		Path game = run.resolve("single-player");
		Files.createDirectories(game.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		lines.add("--- single player");
		lines.addAll(launch(game, false, List.of(), failures));
		report("Library content test", lines, failures);
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
