package oldfaithful.gradle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
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
 * Faithful Core's content APIs end to end: worldgen on a fixed seed with and without a mod's features, then library/api-check on the real server with
 * the hidden client joined to it (and again with the fix mod on the server, whose answer to the 2-9 block update must compose with the core's), then single player.
 */
public abstract class LibraryApiTestTask extends ClientStartupTask {
	private static final int CELLS = 32768;
	private static final int CHUNKS = 49;

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** library/tags.txt, the tag vocabulary. */
	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getTagVocabulary();

	/** The core, settings and the check mod, for the server. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getServerMods();

	/** oldfaithful-fixes, for the server's second run. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getFixesMod();

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

	private Path serverDir(Path run, String name, int port, String extra) throws IOException {
		Path dir = run.resolve(name);
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		for (File mod : getServerMods().getFiles()) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()));
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1",
				"server-port=" + port, "view-distance=3", "max-players=20", "spawn-monsters=false", "pvp=true", extra, ""));
		Files.writeString(dir.resolve("ops.txt"), "OldFaithfulTest\n");
		return dir;
	}

	/** One run's chunks round the spawn. */
	private static final class Dump {
		int spawnX, spawnZ, ore, otherOre, marker;
		byte[][] blocks = new byte[CHUNKS][CELLS];
		char[][] ext = new char[CHUNKS][CELLS];

		static Dump read(Path file) throws IOException {
			Dump d = new Dump();
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
				d.spawnX = in.readInt();
				d.spawnZ = in.readInt();
				d.ore = in.readInt();
				d.otherOre = in.readInt();
				d.marker = in.readInt();
				for (int c = 0; c < CHUNKS; c++) {
					in.readFully(d.blocks[c]);
					for (int i = 0; i < CELLS; i++) d.ext[c][i] = in.readChar();
				}
			}
			return d;
		}

		int count(int block) {
			int n = 0;
			for (char[] chunk : ext) {
				for (char cell : chunk) {
					if ((cell & 0xFFF) == block) n++;
				}
			}
			return n;
		}
	}

	private Dump worldgen(Path run, String mode) throws Exception {
		Path dir = serverDir(run, "worldgen-" + mode, freePort(), "level-seed=12345\nallow-nether=true");
		Path dump = dir.resolve("chunks.bin");
		start(dir, "worldgen", "-Dapicheck.worldgen=" + mode);
		try {
			scene("apitest dumpgen " + dump);
		} finally {
			stop("worldgen " + mode);
		}
		return Dump.read(dump);
	}

	private void compareWorldgen(Dump all, Dump none, Dump other) {
		expect("the spawn didn't move: " + all.spawnX + ", " + all.spawnZ, all.spawnX == none.spawnX && all.spawnZ == none.spawnZ && all.spawnX == other.spawnX && all.spawnZ == other.spawnZ);
		int ore = all.count(all.ore), others = all.count(all.otherOre), markers = all.count(all.marker);
		expect("a mod's ore was placed: " + ore, ore > 500);
		expect("a second feature too: " + others, others > 200);
		expect("the chunk-built feature wrote every chunk: " + markers, markers >= CHUNKS);
		int chunkBuilt = 0;
		for (int c = 0; c < CHUNKS; c++) {
			for (int x = 3; x < 7; x++) {
				int cell = x << 11 | 3 << 7 | 1;
				if ((all.ext[c][cell] & 0xFFF) == all.marker && all.ext[c][cell] >> 12 == 2 && all.blocks[c][cell] == 7) chunkBuilt++;
			}
		}
		expect("each chunk has it, with its meta, over its stand-in: " + chunkBuilt, chunkBuilt == CHUNKS);
		expect("with no features, no overlay", none.count(all.ore) + none.count(all.otherOre) + none.count(all.marker) == 0);
		// Vanilla's lava springs flow as they're placed, by the world's unseeded random, so lava and the air beside it differ between any two runs, with or without mods.
		int moved = 0, lava = 0, firstChunk = -1, firstCell = -1;
		for (int c = 0; c < CHUNKS; c++) {
			for (int i = 0; i < CELLS; i++) {
				int a = all.blocks[c][i] & 0xFF, b = none.blocks[c][i] & 0xFF;
				if (all.ext[c][i] != 0 || a == b) continue;
				if ((a == 0 || a == 10 || a == 11) && (b == 0 || b == 10 || b == 11)) {
					lava++;
				} else if (moved++ == 0) {
					firstChunk = c;
					firstCell = i;
				}
			}
		}
		expect("vanilla's terrain beside them is unchanged, cell for cell, but for " + lava + " of flowing lava: " + moved + " differ" + (moved > 0 ? " (first: chunk " + firstChunk + " cell " + firstCell + ")" : ""), moved == 0);
		int lost = 0, gained = 0;
		for (int c = 0; c < CHUNKS; c++) {
			for (int i = 0; i < CELLS; i++) {
				boolean alone = (other.ext[c][i] & 0xFFF) == all.otherOre, together = (all.ext[c][i] & 0xFFF) == all.otherOre;
				if (alone && !together) lost++;
				if (together && !alone) gained++;
			}
		}
		expect("one feature doesn't move when another joins: " + lost + " lost, " + gained + " gained", lost == 0 && gained == 0 && other.count(all.otherOre) > 200);
	}

	private void clientOnServer(Path run, String name, int port) throws Exception {
		Path game = run.resolve(name);
		Files.createDirectories(game.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		lines.add("--- client: " + name);
		lines.addAll(launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true", "-Dapicheck.clients=true"), failures));
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		Dump all = worldgen(run, "all"), none = worldgen(run, "none"), other = worldgen(run, "other");
		compareWorldgen(all, none, other);

		int port = freePort();
		Path dir = serverDir(run, "server", port, "spawn-animals=true\nallow-nether=false");
		String jvm = "-Dapicheck.tags=" + getTagVocabulary().get().getAsFile();
		start(dir, "first", jvm);
		try {
			scene("apitest prepare");
			clientOnServer(run, "client-on-server", port);
			server.send("save-all");
			server.await(l -> l.contains("Save complete."), 60);
		} finally {
			stop("first");
		}

		for (File mod : getFixesMod().getFiles()) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()));
		start(dir, "with-fixes", jvm);
		try {
			scene("apitest persisted");
			scene("apitest prepare");
			clientOnServer(run, "client-with-fixes", port);
		} finally {
			stop("with-fixes");
		}

		Path game = run.resolve("single-player");
		Files.createDirectories(game.resolve("mods"));
		for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
		prepare(game, false);
		lines.add("--- single player");
		lines.addAll(launch(game, false, List.of("-Dapicheck.clients=true"), failures));
		report("Library API test", lines, failures);
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
