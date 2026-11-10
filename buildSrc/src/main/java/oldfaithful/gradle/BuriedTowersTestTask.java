package oldfaithful.gradle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Buried Towers on really generated worlds, on the real server through library/tower-check. {@code check} makes each seed's square in several visiting orders, across a restart and a crash, and
 * over an older world made without the mod, and compares what stood in them; {@code view} makes a few towers and writes what stands there for {@code tools/tower_view.py}; {@code spacing} writes
 * where sites would stand; {@code compare} runs a seed's with-and-without comparison again on what a {@code check} left.
 * <p>
 * How it's laid out. Every world is a {@link World}: a folder, a server package with some mods in it, and a script (a method under "The worlds") that starts the server there, drives it through
 * its console and checks what comes back. Worlds share nothing, so {@link #runAll} runs a few side by side and prints each one's lines whole, in the order they were listed, whatever order they
 * finish in. What needs two worlds ({@link #withAndWithout}) runs after them. A new check is a step in a script, or a new script and a line in {@link #check}.
 * <p>
 * A world fails by name: a check in its script, a step that didn't finish (the server down, its loop ended on an exception, a listener of ours that threw, or the step's time up), or anything of
 * ours gone wrong in what its servers said. The others carry on, and the task fails at the end with all of it.
 * <p>
 * Where the time goes: each world's line ends with its seconds by step (start, gen, read, save, stop). The servers run their ticks back to back while tower-check loads chunks or counts ticks
 * ({@code Pace} there: the same ticks, without the 50 ms between), at below-normal priority, and one at a time while the user has a game of their own running.
 */
public abstract class BuriedTowersTestTask extends ClientStartupTask {
	private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
	private static final int CELLS = 32768;
	/** Far enough from the spawn that its chunks, made first in every run, are nowhere near. */
	private static final int X0 = 96, Z0 = 96;
	/** {@code Tower.REACH}: how far from its middle a tower's plan can touch. */
	private static final int TOWER_REACH = 18;
	private static final int SPAWNER = 52;

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getServerPackage();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getServerJar();

	/** The core and the test's driver, on every server. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getServerMods();

	/** The mod itself, left out of the runs without it. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getTowerMod();

	/** The editions' server mods, for the run that puts them all under it. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getEditionMods();

	@Input
	public abstract ListProperty<Long> getSeeds();

	/** Chunks each way of the square. */
	@Input
	public abstract Property<Integer> getSize();

	@Input
	public abstract Property<String> getMode();

	/** How many towers to dump a seed in {@code view}. */
	@Input
	public abstract Property<Integer> getTowers();

	/** In {@code view}, the towers round these places instead ({@code x,z;x,z}), any cell in each one's square. */
	@Input
	@Optional
	public abstract Property<String> getAt();

	/** The style the world's towers are made in, in any mode: the rule's default, simple, unless told {@code complex}. */
	@Input
	@Optional
	public abstract Property<String> getStyle();

	/** How many worlds' servers run side by side. A server is one busy thread and up to 1.4 GB. */
	@Input
	public abstract Property<Integer> getParallel();

	/** Ticks back to back while the driver waits on them; false for the game's own 20 a second, as the test ran until 2026-11-09. */
	@Input
	public abstract Property<Boolean> getHurry();

	/**
	 * Every world's square written to its {@code cells.bin}, 150 MB each, for a look at a world that went wrong; without it, only the squares the task reads back ({@link #withAndWithout}, the
	 * older world's).
	 */
	@Input
	public abstract Property<Boolean> getDumps();

	/** {@code tools/user_busy.ps1}, this project's own PC's script and not in the public repositories: prints what the person at the PC is running that says they're using it, or nothing. Without the file, nobody counts as playing. */
	@Internal
	public abstract RegularFileProperty getUserBusy();

	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	/** The inputs, read once on Gradle's own thread: the worlds' threads only see these. */
	private Path serverPackage, serverJar;
	private List<File> serverMods, towerMod, editionMods;
	private int size, towers;
	private String style, at, java;
	private boolean hurry, dumps;

	// ---- A world and its servers

	private interface Script {
		void run(World world) throws Exception;
	}

	private enum Mods {
		NO_TOWERS, TOWERS, TOWERS_AND_EDITIONS
	}

	/** How a square's making is interrupted. */
	private enum Through {
		NOTHING, RESTART, CRASH
	}

	/** One world: its folder, the server running in it now, and everything that came of it, held until {@link #runAll} prints it in its turn. */
	private final class World {
		final long seed;
		final String label;
		final Path dir;
		private final Script script;
		private final List<String> said = new ArrayList<>(), failed = new ArrayList<>(), output = new ArrayList<>();
		private final Map<String, Long> spent = new LinkedHashMap<>();
		private volatile GameProcess server;
		private int port;
		private long took;

		World(Path run, long seed, String folder, String label, Script script) {
			this.seed = seed;
			this.label = label;
			this.dir = run.resolve(folder);
			this.script = script;
		}

		/** The whole of it, on a thread of its own. Never throws: what went wrong is among its failures. */
		void run() {
			long began = System.nanoTime();
			try {
				script.run(this);
			} catch (Exception e) {
				failed.add(label + ": " + e);
				if (e instanceof InterruptedException) Thread.currentThread().interrupt();
			} finally {
				try {
					if (server != null) stop("after what went wrong");
				} catch (Exception e) {
					kill();
				}
			}
			took = System.nanoTime() - began;
			String steps = spent.entrySet().stream().map(s -> s.getKey() + " " + tenths(s.getValue())).collect(Collectors.joining(", "));
			say("    " + label + ": " + tenths(took) + " s (" + steps + ")");
		}

		void kill() {
			GameProcess running = server;
			if (running != null) running.close();
		}

		void say(String line) {
			said.add(line);
		}

		void expect(String what, boolean ok) {
			say((ok ? "ok  " : "FAIL") + ": " + what);
			if (!ok) failed.add(what);
		}

		private void spend(String step, long began) {
			spent.merge(step, System.nanoTime() - began, Long::sum);
		}

		/** The server package with this world's mods, as an owner would set it up. */
		void install(Mods mods) throws IOException {
			long began = System.nanoTime();
			copyTree(serverPackage, dir);
			Files.copy(serverJar, dir.resolve("minecraft_server.jar"));
			List<File> jars = new ArrayList<>(serverMods);
			if (mods != Mods.NO_TOWERS) jars.addAll(towerMod);
			if (mods == Mods.TOWERS_AND_EDITIONS) jars.addAll(editionMods);
			for (File jar : jars) addMod(jar);
			Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=3",
					"max-players=20", "spawn-monsters=false", "spawn-animals=false", "allow-nether=false", "level-seed=" + seed, ""));
			if (mods != Mods.NO_TOWERS && style != null) {
				Files.createDirectories(dir.resolve("config"));
				Files.writeString(dir.resolve("config").resolve("faithful-rules.properties"), "rule.buried-towers.style=" + style + "\n");
			}
			spend("start", began);
		}

		void addMod(File jar) throws IOException {
			Files.copy(jar.toPath(), dir.resolve("mods").resolve(jar.getName()), StandardCopyOption.REPLACE_EXISTING);
		}

		/** Starts the server by its own script; {@code part} names this start's log, {@code harness-output-<part>.log}. */
		void start(String part) throws Exception {
			long began = System.nanoTime();
			// Below normal priority, which the script's Java inherits, so side-by-side servers give way to whatever else the PC is doing.
			List<String> command = WINDOWS ? List.of("cmd", "/c", "start", "\"\"", "/b", "/wait", "/belownormal", "cmd", "/c", dir.resolve("start.bat").toString())
					: List.of("nice", "-n", "10", "sh", dir.resolve("start.sh").toString());
			server = new GameProcess(command, dir, dir.resolve("harness-output-" + part + ".log"), Map.of("JAVA", java, "JAVA_OPTS", "-Xmx1400m" + (hurry ? " -Dtowercheck.hurry=true" : "")));
			if (server.await(l -> l.contains("Done ("), 300) == null) throw new IOException("the server didn't come up: " + part);
			spend("start", began);
		}

		/** Stops the server as an owner would and holds its output to the rules: nothing of ours gone wrong in it. {@code part} says which of the world's servers this was, if it has several. */
		void stop(String part) throws Exception {
			long began = System.nanoTime();
			String what = part.isEmpty() ? label : label + ", " + part;
			try {
				server.send("stop");
			} catch (IOException gone) {
				// What it said on its way down is still read below. A step that failed on it has said so already.
				if (failed.isEmpty()) failed.add(what + ": the server was down before it was told to stop");
			}
			if (server.waitFor(180) == null) failed.add(what + ": the server didn't stop within 3 minutes");
			List<String> out = ended(what);
			for (String p : problems(out)) failed.add(what + ": " + p);
			for (String line : out) {
				if (line.contains("[faithful-core] WARN")) failed.add(what + ": " + line);
			}
			spend("stop", began);
		}

		/** Has the server save a few chunks and die mid-way, as a crash or a power cut would leave the world. */
		void crash() throws Exception {
			long began = System.nanoTime();
			server.send("towertest crash 6");
			if (server.waitFor(120) == null) failed.add(label + ": the server didn't go down");
			ended(label + ", crashed");
			spend("stop", began);
		}

		private List<String> ended(String what) {
			List<String> out = server.lines();
			server.close();
			server = null;
			output.add("--- " + what);
			output.addAll(out);
			return out;
		}

		/** A console command, and the line that says it's done. {@code step} is where its time is counted. */
		String command(String step, String text, String marker, int seconds) throws Exception {
			long began = System.nanoTime();
			int from = server.lineCount();
			server.send(text);
			String line = server.awaitFrom(from, l -> l.contains(marker) || cannotFinish(l), seconds);
			spend(step, began);
			if (line == null) {
				// Down, or the time up. The exit code is the start script's wrapper's, 0 whatever became of the server, so it isn't given.
				throw new IOException(server.waitFor(0) != null ? "the server went down before \"" + marker + "\" of \"" + text + "\""
						: "no \"" + marker + "\" within " + seconds + " s of \"" + text + "\"");
			}
			if (!line.contains(marker)) throw new IOException("\"" + text + "\" can't finish, as the server said: " + clip(line.trim()));
			return line;
		}

		/** A square of chunks loaded in an order, eight a tick ({@code count} of them, or all if 0), then forty ticks for what flows and falls to start moving. */
		void generate(int x0, int z0, int size, String order, int count) throws Exception {
			command("gen", "towertest gen " + x0 + " " + z0 + " " + size + " " + order + " 8" + (count > 0 ? " " + count : ""), "[tower-check] DONE gen", 900);
			command("gen", "towertest settle", "[tower-check] DONE settle", 300);
		}

		void saveAll() throws Exception {
			command("save", "save-all", "Save complete.", 120);
		}

		/** A driver command that writes a file in the world's folder; the file. */
		Path read(String what, String arguments, String file, int seconds) throws Exception {
			Path path = dir.resolve(file).toAbsolutePath();
			command("read", "towertest " + what + " " + arguments + " " + path, "[tower-check] DONE " + what, seconds);
			return path;
		}

		/** Every cell of a square of chunks, for {@link Square#read}. */
		Path cells(int size, String file) throws Exception {
			return read("cells", X0 + " " + Z0 + " " + size, file, 180);
		}

		/** Where the mod would put towers in the test's square. */
		List<Site> sites() throws Exception {
			List<Site> found = new ArrayList<>();
			for (String line : Files.readAllLines(read("sites", X0 + " " + Z0 + " " + size, "sites.txt", 300))) {
				String[] p = line.split(" ");
				Site s = new Site();
				s.x = Integer.parseInt(p[0]);
				s.z = Integer.parseInt(p[1]);
				s.climate = p[2];
				s.line = line;
				found.add(s);
			}
			return found;
		}

		/**
		 * The driver's report on the test's square, a line a site: every finished cell of every standing tower must be as planned, but for {@code allowed} of them. {@code whole} if towers are
		 * expected to stand at all. The lines, for a script that wants more of them.
		 */
		List<String> reports(String name, boolean whole, int allowed) throws Exception {
			List<String> report = Files.readAllLines(read("report", X0 + " " + Z0 + " " + size, "report-" + name.replace(' ', '-') + ".txt", 300));
			int standing = 0;
			for (String line : report) {
				say("    " + name + ": " + clip(line));
				if (line.contains("stands=true")) {
					standing++;
					expect(name + ": as planned: " + clip(line), wrong(line) <= allowed);
				}
			}
			if (whole) expect(name + ": some towers stand: " + standing, standing > 0);
			return report;
		}

		/** Every iron door with a lever by it stands closed across its doorway, and its lever opens it out of the way, in the real server's own rules. */
		void doors() throws Exception {
			String good = "before closed,across pulled open,aside back closed,across";
			List<String> doors = Files.readAllLines(read("doors", X0 + " " + Z0 + " " + size, "doors.txt", 300));
			long passed = doors.stream().filter(l -> l.contains(good)).count();
			for (String line : doors) if (!line.contains(good)) say("    " + label + ": door " + line);
			expect(label + ": iron doors start closed and their levers open them: " + passed + " of " + doors.size(), passed == doors.size() && passed > 0);
		}
	}

	private static final class Site {
		int x, z;
		String climate, line;
	}

	/**
	 * A line after which a step would only run out its time: beta's loop ended on an exception (the server stays up for its console, ticking nothing), a console command threw, or a listener of
	 * ours threw (the core skips it, and the driver's is how chunks get loaded: it would be tried again each tick, hurried, until the time was up).
	 */
	private static boolean cannotFinish(String line) {
		return line.contains("Unexpected exception") || line.contains("[faithful-core] WARN") && line.contains("threw, and was skipped");
	}

	private static String tenths(long nanos) {
		return String.format(Locale.ROOT, "%.1f", nanos / 1e9);
	}

	private static String clip(String line) {
		return line.length() > 200 ? line.substring(0, 200) : line;
	}

	/** A report line's count of cells that aren't as the tower's plan has them. */
	private static int wrong(String line) {
		return Integer.parseInt(line.replaceAll(".* wrong=(\\d+).*", "$1"));
	}

	/**
	 * Runs the worlds, a few at a time, and prints what each said in the order they're listed. The ones in {@code slowest} start first: the run is over when the last server stops, so a long one
	 * shouldn't be the last to start.
	 */
	private void runAll(List<World> worlds, List<World> slowest) throws Exception {
		int atOnce = worlds.size() == 1 ? 1 : Math.min(atOnce(), worlds.size());
		// No client joins these servers, but each needs a port of its own to open: all picked together, so no two are the same.
		List<ServerSocket> sockets = new ArrayList<>();
		for (World w : worlds) {
			ServerSocket socket = new ServerSocket(0);
			sockets.add(socket);
			w.port = socket.getLocalPort();
		}
		for (ServerSocket socket : sockets) socket.close();
		long began = System.nanoTime(), sum = 0;
		ExecutorService pool = Executors.newFixedThreadPool(atOnce);
		try {
			Map<World, Future<?>> running = new LinkedHashMap<>();
			for (World w : slowest) running.put(w, pool.submit(w::run));
			for (World w : worlds) running.computeIfAbsent(w, starting -> pool.submit(starting::run));
			for (World w : worlds) {
				running.get(w).get();
				for (String line : w.said) getLogger().lifecycle("{}", line);
				failures.addAll(w.failed);
				lines.addAll(w.output);
				sum += w.took;
			}
		} finally {
			// Stopped from outside: no server outlives the task.
			pool.shutdownNow();
			for (World w : worlds) w.kill();
		}
		if (worlds.size() > 1) getLogger().lifecycle("    {} worlds, {} at a time: {} s, where one after another takes {} s", worlds.size(), atOnce, tenths(System.nanoTime() - began), tenths(sum));
	}

	/** As many as asked, but one while the user has a game or heavy app of their own running, as this project's own queue for game launches does with its slots (tools/gwl.sh, not in the public repositories either). */
	private int atOnce() throws InterruptedException {
		int asked = Math.max(1, getParallel().get());
		if (asked == 1 || !WINDOWS || !getUserBusy().isPresent() || !getUserBusy().get().getAsFile().isFile()) return asked;
		try {
			Process check = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", getUserBusy().get().getAsFile().getAbsolutePath())
					.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			check.getOutputStream().close();
			if (!check.waitFor(30, TimeUnit.SECONDS)) {
				check.destroyForcibly();
				return asked;
			}
			String busy = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim().replaceAll("\\s*\\R\\s*", "; ");
			if (busy.isEmpty()) return asked;
			getLogger().lifecycle("    one world at a time: the user is running {}", busy);
			return 1;
		} catch (IOException e) {
			return asked;
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

	// ---- The worlds

	/** {@code check}: eight worlds a seed, then what two of them show together. */
	private void check(Path run) throws Exception {
		List<World> worlds = new ArrayList<>(), slowest = new ArrayList<>();
		for (long seed : getSeeds().get()) {
			worlds.add(new World(run, seed, seed + "-plain", seed + " plain", w -> square(w, Mods.NO_TOWERS, "rows", Through.NOTHING, true)));
			worlds.add(new World(run, seed, seed + "-rows", seed + " rows", w -> square(w, Mods.TOWERS, "rows", Through.NOTHING, true)));
			// Each of these is held against its plan as it's made; its cells are compared with nothing, as beta's own world differs by order, and written only if asked.
			worlds.add(new World(run, seed, seed + "-rings", seed + " rings", w -> square(w, Mods.TOWERS, "rings", Through.NOTHING, dumps)));
			worlds.add(new World(run, seed, seed + "-shuffled", seed + " shuffled", w -> square(w, Mods.TOWERS, "shuffle" + seed, Through.RESTART, dumps)));
			// Three times as long as any other: Old Faithful's block ticks, saved with their chunks, are gone through once for every chunk at the stop.
			World back = new World(run, seed, seed + "-back", seed + " back", w -> square(w, Mods.TOWERS_AND_EDITIONS, "back", Through.NOTHING, dumps));
			worlds.add(back);
			slowest.add(back);
			worlds.add(new World(run, seed, seed + "-crash", seed + " crash", w -> square(w, Mods.TOWERS, "shuffle" + (seed + 1), Through.CRASH, dumps)));
			worlds.add(new World(run, seed, seed + "-older", seed + " older", this::olderWorld));
			worlds.add(new World(run, seed, seed + "-lost", seed + " lost", this::lostChunk));
		}
		runAll(worlds, slowest);
		compare(run);
	}

	/**
	 * What two of a seed's worlds show together. {@code compare} is this alone, with no server started, on the squares a run left in the run folder: for a change to the comparison itself, or a
	 * second look at a run.
	 */
	private void compare(Path run) {
		for (long seed : getSeeds().get()) {
			try {
				withAndWithout(run, seed);
			} catch (IOException e) {
				// A square half written by a server that went down; that world's own failure says more.
				failures.add("seed " + seed + ": with and without the mod couldn't be compared: " + e);
			}
		}
	}

	/**
	 * The test's square made in one visiting order, perhaps with a restart or a crash half-way: every tower in it as planned, and with {@code cells} its cells written to {@code cells.bin}. After
	 * a restart, the iron doors too.
	 */
	private void square(World w, Mods mods, String order, Through through, boolean cells) throws Exception {
		w.install(mods);
		w.start("first");
		if (through == Through.RESTART) {
			w.generate(X0, Z0, size, order, size * size / 2);
			w.saveAll();
			w.stop("first half");
			w.start("second");
		} else if (through == Through.CRASH) {
			w.generate(X0, Z0, size, order, size * size / 2);
			w.saveAll();
			w.generate(X0, Z0, size, order, 0);
			w.crash();
			w.start("second");
		}
		w.generate(X0, Z0, size, order, 0);
		if (cells) w.cells(size, "cells.bin");
		// After a crash a chunk that's lost decorates again into its sealed neighbours, which are left alone as they may be the player's by then: a few cells of beta's veins in a wall.
		if (mods != Mods.NO_TOWERS) w.reports(w.label, true, through == Through.CRASH ? 400 : 0);
		if (through == Through.RESTART) w.doors();
		w.stop("");
	}

	/**
	 * An older world: a square made with no mod, then the mod put in and the land round it made. A tower that would cross what's already there isn't built, whenever and in whatever order its other
	 * chunks come, and the old chunks don't change.
	 */
	private void olderWorld(World w) throws Exception {
		int old = size / 2;
		w.install(Mods.NO_TOWERS);
		w.start("without the mod");
		w.generate(X0, Z0, old, "rows", 0);
		Square before = Square.read(w.cells(old, "before.bin"));
		w.saveAll();
		w.stop("without the mod");

		for (File jar : towerMod) w.addMod(jar);
		w.start("with the mod");
		w.generate(X0, Z0, size, "shuffle" + w.seed, 0);
		Square after = Square.read(w.cells(old, "after.bin"));
		List<String> report = w.reports(w.seed + " older world", false, 0);
		w.saveAll();
		w.stop("with the mod");

		w.start("again");
		w.reports(w.seed + " older world, reopened", false, 0);
		w.stop("again");

		long moved = 0;
		String sample = "";
		// Its last row and column of chunks still had decoration to come when the mod arrived.
		for (int cx = 0; cx < old - 1; cx++) {
			for (int cz = 0; cz < old - 1; cz++) {
				int c = cx * old + cz;
				if (!before.populated[c]) continue;
				for (int i = 0; i < CELLS; i++) {
					int p = before.ids[c][i] & 0xFF, q = after.ids[c][i] & 0xFF;
					if (p != q && !flowed(p, q)) {
						moved++;
						if (sample.length() < 100) sample += " " + i + ":" + p + ">" + q;
					}
				}
			}
		}
		w.expect(w.seed + ": the older chunks are as they were (" + moved + " cells differ" + sample + ")", moved == 0);
		// Every site that reaches the old square has to have stayed out; the report said so of each.
		boolean crossed = false, kept = false;
		for (String line : report) {
			String[] p = line.split(" ");
			int x = Integer.parseInt(p[0]), z = Integer.parseInt(p[1]);
			int x0 = x - TOWER_REACH >> 4, x1 = x + TOWER_REACH >> 4, z0 = z - TOWER_REACH >> 4, z1 = z + TOWER_REACH >> 4;
			boolean crosses = x0 <= X0 + old && z0 <= Z0 + old && x1 >= X0 && z1 >= Z0;
			boolean stands = line.contains("stands=true");
			if (x0 < X0 + old - 1 && z0 < Z0 + old - 1 && x1 >= X0 && z1 >= Z0) w.expect(w.seed + ": a site in the old square doesn't stand: " + p[0] + "," + p[1], !stands);
			crossed |= crosses && !stands;
			kept |= !crosses && stands;
		}
		w.say("    seed " + w.seed + ": older world: a tower held back " + crossed + ", one built past it " + kept);
	}

	/** A chunk of a tower lost from the save comes back as part of the tower, and what a player did to a chunk beside it stays. */
	private void lostChunk(World w) throws Exception {
		w.install(Mods.TOWERS);
		w.start("first");
		List<Site> sites = w.sites();
		if (sites.isEmpty()) {
			w.stop("");
			return;
		}
		Site site = sites.get(0);
		int cx = site.x >> 4, cz = site.z >> 4, reach = 5, side = reach * 2 + 1;
		// A player's cobble high in the air, over a chunk that isn't the one lost.
		String mark = ((cx + 1) * 16 + 2) + " 120 " + (cz * 16 + 2);
		w.generate(cx - reach, cz - reach, side, "rows", 0);
		w.command("read", "towertest set " + mark + " 4", "[tower-check] DONE set", 30);
		w.saveAll();
		w.stop("first");

		zeroChunk(w.dir.resolve("world/region"), cx, cz);
		w.start("second");
		w.generate(cx - reach, cz - reach, side, "rows", 0);
		for (String line : Files.readAllLines(w.read("report", (cx - reach) + " " + (cz - reach) + " " + side, "report-lost.txt", 120))) {
			if (!line.startsWith(site.x + " " + site.z + " ")) continue;
			w.say("    lost: " + clip(line));
			// A neighbour's sealed cells can't be restored once the chunk's decoration runs into them again, so a few cells may be vanilla's; none should be missing from the lost chunk's own column of cells.
			w.expect(w.seed + ": a lost chunk came back with its tower, " + wrong(line) + " cells off", line.contains("stands=true") && wrong(line) < 800);
		}
		String got = w.command("read", "towertest get " + mark, "[tower-check] DONE get", 30);
		w.expect(w.seed + ": a player's block in the chunk beside it is still there: " + got, got.trim().endsWith(" 4"));
		w.stop("second");
	}

	/** Takes one chunk out of a region's table, as a damaged save loses it. */
	private static void zeroChunk(Path regions, int cx, int cz) throws IOException {
		Path file = regions.resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mcr");
		try (java.io.RandomAccessFile region = new java.io.RandomAccessFile(file.toFile(), "rw")) {
			region.seek(4L * ((cx & 31) + (cz & 31) * 32));
			region.writeInt(0);
		}
	}

	/** {@code view}: a few towers from each seed, one of each climate first: real ground under them, written for the viewer. */
	private void view(Path run) throws Exception {
		List<World> worlds = new ArrayList<>();
		for (long seed : getSeeds().get()) worlds.add(new World(run, seed, Long.toString(seed), "seed " + seed, this::viewTowers));
		runAll(worlds, List.of());
	}

	private void viewTowers(World w) throws Exception {
		w.install(Mods.TOWERS);
		w.start("view");
		if (at != null) {
			for (String place : at.split(";")) {
				int x = Integer.parseInt(place.split(",")[0].trim()), z = Integer.parseInt(place.split(",")[1].trim());
				dumpTower(w, x, z, "at-" + x + "_" + z);
			}
		} else {
			List<Site> all = w.sites();
			w.say("    seed " + w.seed + ": " + all.size() + " sites in the square");
			List<Site> picks = new ArrayList<>();
			List<String> climates = new ArrayList<>();
			for (Site s : all) {
				if (!climates.contains(s.climate)) {
					climates.add(s.climate);
					picks.add(s);
				}
			}
			for (Site s : all) if (!picks.contains(s)) picks.add(s);
			for (Site s : picks.subList(0, Math.min(picks.size(), towers))) {
				w.say("    " + s.line);
				dumpTower(w, s.x, s.z, s.climate + "-" + s.x + "_" + s.z);
			}
		}
		w.stop("");
	}

	/** Makes the land round a place and writes the tower of its square into {@code towers/<name>.bin}. */
	private static void dumpTower(World w, int x, int z, String name) throws Exception {
		int reach = 4;
		w.generate((x >> 4) - reach, (z >> 4) - reach, reach * 2 + 1, "rows", 0);
		w.read("tower", x + " " + z, "towers/" + name + ".bin", 120);
	}

	/**
	 * {@code spacing}: where each seed's sites would stand over a wide square of squares, at the margin in use and the first one, for the spacing between towers:
	 * {@code <run>/<seed>/places-<margin>.txt}.
	 */
	private void spacing(Path run) throws Exception {
		List<World> worlds = new ArrayList<>();
		for (long seed : getSeeds().get()) {
			worlds.add(new World(run, seed, Long.toString(seed), "seed " + seed, w -> {
				w.install(Mods.TOWERS);
				w.start("spacing");
				for (int margin : new int[] {19, 100, 112, 128}) {
					Path file = w.read("places", "-24 -24 48 " + margin, "places-" + margin + ".txt", 900);
					w.say("    seed " + w.seed + ", margin " + margin + ": " + Files.readAllLines(file).size() + " sites");
				}
				w.stop("");
			}));
		}
		runAll(worlds, List.of());
	}

	// ---- What stood in a square, and two squares compared

	/** A square of chunks as it stood. */
	private static final class Square {
		int x0, z0, size;
		byte[][] ids;
		boolean[] populated;

		static Square read(Path file) throws IOException {
			Square s = new Square();
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
				s.x0 = in.readInt();
				s.z0 = in.readInt();
				s.size = in.readInt();
				s.ids = new byte[s.size * s.size][CELLS];
				s.populated = new boolean[s.size * s.size];
				for (int c = 0; c < s.ids.length; c++) {
					s.populated[c] = in.readByte() != 0;
					in.readFully(s.ids[c]);
					in.skipBytes(CELLS);
				}
			}
			return s;
		}

		int count(int id) {
			int n = 0;
			for (byte[] cells : ids) {
				for (byte c : cells) if ((c & 0xFF) == id) n++;
			}
			return n;
		}

		/** Where in the world cell {@code i} of chunk {@code c} is: x, y, z. */
		int[] place(int c, int i) {
			return new int[] {(c / size + x0) * 16 + (i >> 11), i & 127, (c % size + z0) * 16 + (i >> 7 & 15)};
		}
	}

	private static boolean fluid(int id) {
		return id == 0 || id >= 8 && id <= 11;
	}

	private static boolean flowMade(int id) {
		return id == 1 || id == 4 || id == 49 || id >= 37 && id <= 40 || id == 31 || id == 32;
	}

	private static boolean falling(int id) {
		return id == 12 || id == 13;
	}

	/** A cell that differs only by what flows and falls, which goes by the world's own unseeded chance. */
	private static boolean flowed(int p, int q) {
		return fluid(p) && (fluid(q) || flowMade(q) || falling(q)) || fluid(q) && (flowMade(p) || falling(p));
	}

	/** A check that isn't one world's, printed as theirs are. */
	private void expect(String what, boolean ok) {
		getLogger().lifecycle("{}: {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	/**
	 * A seed's square with the mod against without it, both made in rows: the spawners and the cells that differ, for a look, and the ore, which is held to beta's ({@link #oreAsBeta}). The squares
	 * are the two worlds' {@code cells.bin}, and where the towers are comes from the report the world with the mod wrote ({@code World.reports}).
	 */
	private void withAndWithout(Path run, long seed) throws IOException {
		Path without = run.resolve(seed + "-plain").resolve("cells.bin"), with = run.resolve(seed + "-rows").resolve("cells.bin");
		Path report = run.resolve(seed + "-rows").resolve("report-" + seed + "-rows.txt");
		if (!Files.exists(without) || !Files.exists(with) || !Files.exists(report)) {
			expect("seed " + seed + ": with and without the mod can't be compared, as a world's square or its report isn't in " + run, false);
			return;
		}
		Square plain = Square.read(without), rows = Square.read(with);
		List<int[]> sites = new ArrayList<>();
		for (String line : Files.readAllLines(report)) {
			String[] p = line.split(" ");
			sites.add(new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])});
		}
		getLogger().lifecycle("    seed {}: {} spawners without the mod, {} with it", seed, plain.count(SPAWNER), rows.count(SPAWNER));
		long solid = 0, flow = 0;
		List<String> samples = new ArrayList<>();
		for (int c = 0; c < plain.ids.length; c++) {
			if (!plain.populated[c] || !rows.populated[c]) continue;
			int cx = c / plain.size + plain.x0, cz = c % plain.size + plain.z0;
			for (int i = 0; i < CELLS; i++) {
				int p = plain.ids[c][i] & 0xFF, q = rows.ids[c][i] & 0xFF;
				if (p == q) continue;
				if (flowed(p, q)) {
					flow++;
				} else {
					solid++;
					if (samples.size() < 12) samples.add((cx * 16 + (i >> 11)) + "," + (i & 127) + "," + (cz * 16 + (i >> 7 & 15)) + ": " + p + " against " + q);
				}
			}
		}
		getLogger().lifecycle("    seed {}, with and without the mod over the whole square: {} solid cells, {} by flow; {}", seed, solid, flow, samples);
		oreAsBeta(plain, rows, sites, seed);
	}

	private static boolean ore(int id) {
		return id >= 14 && id <= 16 || id == 21 || id == 56 || id == 73;
	}

	/**
	 * Ore with the mod is where beta puts it. The mod changes ore in two kinds of place and no other: the chunks a tower reaches, where it stands in the rock, and the rooms of beta's dungeons, none
	 * of which it lets be made. Beta carves a dungeon's room before it lays a chunk's ore, so without the room the veins run on through the rock it would have taken; and the core's veto makes
	 * every random draw the dungeon would have, so all that comes after it in the chunk lands where it did. An ore cell that differs anywhere else is the mod moving beta's ore, and fails the test,
	 * by chunk. Ore doesn't flow, fall or grow, so unlike the rest of the square it's the same from run to run.
	 * <p>
	 * A room is known by its spawner, there without the mod and not with it: the cells beta's {@code DungeonFeature} writes are within 4 of it across and from 1 under it to 3 over, and in them
	 * only ore the mod's world has where beta's has none is the room's doing. A room whose spawner something took afterwards (a lake of a chunk made later could) would be read as a failure here;
	 * the seeds in use have none.
	 */
	private void oreAsBeta(Square plain, Square with, List<int[]> sites, long seed) {
		List<int[]> rooms = new ArrayList<>();
		for (int c = 0; c < plain.ids.length; c++) {
			for (int i = 0; i < CELLS; i++) {
				if ((plain.ids[c][i] & 0xFF) == SPAWNER && (with.ids[c][i] & 0xFF) != SPAWNER) rooms.add(plain.place(c, i));
			}
		}
		long inTowers = 0, inRooms = 0, elsewhere = 0;
		StringBuilder where = new StringBuilder();
		for (int c = 0; c < plain.ids.length; c++) {
			if (!plain.populated[c] || !with.populated[c]) continue;
			int cx = c / plain.size + plain.x0, cz = c % plain.size + plain.z0, here = 0;
			boolean tower = false;
			for (int[] s : sites) tower |= cx >= s[0] - TOWER_REACH >> 4 && cx <= s[0] + TOWER_REACH >> 4 && cz >= s[1] - TOWER_REACH >> 4 && cz <= s[1] + TOWER_REACH >> 4;
			String first = "";
			for (int i = 0; i < CELLS; i++) {
				int p = plain.ids[c][i] & 0xFF, q = with.ids[c][i] & 0xFF;
				if (p == q || !ore(p) && !ore(q)) continue;
				if (tower) {
					inTowers++;
					continue;
				}
				int[] at = plain.place(c, i);
				boolean room = false;
				if (ore(q) && !ore(p)) {
					for (int[] r : rooms) room |= Math.abs(at[0] - r[0]) <= 4 && Math.abs(at[2] - r[2]) <= 4 && at[1] >= r[1] - 1 && at[1] <= r[1] + 3;
				}
				if (room) {
					inRooms++;
				} else if (here++ == 0) {
					first = at[0] + "," + at[1] + "," + at[2] + " is " + p + " without it and " + q + " with";
				}
			}
			if (here > 0) {
				elsewhere += here;
				if (where.length() < 400) where.append(elsewhere == here ? ": " : "; ").append(here).append(" in chunk ").append(cx).append(',').append(cz).append(" (").append(first).append(')');
			}
		}
		String counted = "seed " + seed + ": ore with the mod is beta's but where a tower stands or a dungeon isn't made: " + inTowers + " cells differ in the towers' chunks, " + inRooms
				+ " in the rooms of " + rooms.size() + " dungeons, " + elsewhere + " anywhere else" + where;
		expect(counted, elsewhere == 0);
		lines.add(counted);
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		serverPackage = getServerPackage().get().getAsFile().toPath();
		serverJar = getServerJar().get().getAsFile().toPath();
		serverMods = new ArrayList<>(getServerMods().getFiles());
		towerMod = new ArrayList<>(getTowerMod().getFiles());
		editionMods = new ArrayList<>(getEditionMods().getFiles());
		size = getSize().get();
		style = getStyle().getOrNull();
		at = getAt().getOrNull();
		towers = getTowers().get();
		dumps = getDumps().get();
		java = getJavaExecutable().get();
		hurry = getHurry().get();
		String mode = getMode().get();
		if (mode.equals("compare")) {
			// What a run left in its folder, which stays as it is.
			compare(getRunDir().get().getAsFile().toPath());
		} else {
			Path run = freshRunDir();
			if (mode.equals("view")) view(run);
			else if (mode.equals("spacing")) spacing(run);
			else check(run);
			Files.write(run.resolve("harness-output.log"), lines);
		}
		if (!failures.isEmpty()) throw new GradleException("Buried Towers' test failed:\n" + String.join("\n", failures));
	}
}
