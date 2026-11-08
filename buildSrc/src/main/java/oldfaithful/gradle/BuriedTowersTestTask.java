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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Buried Towers on really generated worlds, on the real server through library/tower-check. {@code view} makes a few towers and writes what stands there for {@code tools/tower_view.py}; {@code check}
 * makes each seed's square in several visiting orders, across a restart and a crash, and over an older world made without the mod, and compares what stood in them.
 */
public abstract class BuriedTowersTestTask extends ClientStartupTask {
	private static final int CELLS = 32768;
	/** Far enough from the spawn that its chunks, made first in every run, are nowhere near. */
	private static final int X0 = 96, Z0 = 96;
	/** {@code Tower.REACH}: how far from its middle a tower's plan can touch. */
	private static final int TOWER_REACH = 18;

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

	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private GameProcess server;

	/** A square of chunks as it stood. */
	private static final class Square {
		int x0, z0, size;
		byte[][] ids;
		boolean[] populated;

		int id(int x, int y, int z) {
			if (y < 0 || y >= 128) return 0;
			int cx = (x >> 4) - x0, cz = (z >> 4) - z0;
			if (cx < 0 || cz < 0 || cx >= size || cz >= size) return -1;
			return ids[cx * size + cz][(x & 15) << 11 | (z & 15) << 7 | y] & 0xFF;
		}

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
	}

	private static final class Site {
		int x, z;
		String climate, purpose, line;
	}

	private void expect(String what, boolean ok) {
		getLogger().lifecycle("{}: {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	private void awaitAfter(int from, String marker, int seconds) throws Exception {
		if (server.awaitFrom(from, l -> l.contains(marker), seconds) == null) throw new IOException("no \"" + marker + "\" within " + seconds + " s");
	}

	private void command(String text, String marker, int seconds) throws Exception {
		int from = server.lineCount();
		server.send(text);
		awaitAfter(from, marker, seconds);
	}

	private void start(Path dir, String label) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		server = new GameProcess(command, dir, dir.resolve("harness-output-" + label + ".log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx1400m"));
		if (server.await(l -> l.contains("Done ("), 300) == null) throw new IOException("the server didn't come up: " + label);
	}

	private void stop(String label) throws Exception {
		server.send("stop");
		Integer exit = server.waitFor(180);
		if (exit == null) failures.add(label + ": the server didn't stop within 3 minutes");
		List<String> out = server.lines();
		server.close();
		for (String p : problems(out)) failures.add(label + ": " + p);
		for (String line : out) {
			if (line.contains("[faithful-core] WARN")) failures.add(label + ": " + line);
		}
		lines.add("--- " + label);
		lines.addAll(out);
	}

	private void serverDir(Path dir, long seed, boolean towers, boolean editions) throws IOException {
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		List<File> mods = new ArrayList<>(getServerMods().getFiles());
		if (towers) mods.addAll(getTowerMod().getFiles());
		if (editions) mods.addAll(getEditionMods().getFiles());
		for (File mod : mods) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()), StandardCopyOption.REPLACE_EXISTING);
		writeProperties(dir, seed);
		if (towers && getStyle().isPresent()) {
			Files.createDirectories(dir.resolve("config"));
			Files.writeString(dir.resolve("config").resolve("faithful-rules.properties"), "rule.buried-towers.style=" + getStyle().get() + "\n");
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

	private static void writeProperties(Path dir, long seed) throws IOException {
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=3",
				"max-players=20", "spawn-monsters=false", "spawn-animals=false", "allow-nether=false", "level-seed=" + seed, ""));
	}

	private void generate(int x0, int z0, int size, String order, int count) throws Exception {
		command("towertest gen " + x0 + " " + z0 + " " + size + " " + order + " 8" + (count > 0 ? " " + count : ""), "[tower-check] DONE gen", 900);
		command("towertest settle", "[tower-check] DONE settle", 300);
	}

	private List<Site> sites(Path dir) throws Exception {
		Path file = dir.resolve("sites.txt").toAbsolutePath();
		command("towertest sites " + X0 + " " + Z0 + " " + getSize().get() + " " + file, "[tower-check] DONE sites", 300);
		List<Site> found = new ArrayList<>();
		for (String line : Files.readAllLines(file)) {
			String[] p = line.split(" ");
			Site s = new Site();
			s.x = Integer.parseInt(p[0]);
			s.z = Integer.parseInt(p[1]);
			s.climate = p[2];
			s.purpose = p[3];
			s.line = line;
			found.add(s);
		}
		return found;
	}

	/** A few towers from each seed, one of each climate first: real ground under them, written for the viewer. */
	private void view(Path run) throws Exception {
		for (long seed : getSeeds().get()) {
			Path dir = run.resolve(Long.toString(seed));
			serverDir(dir, seed, true, false);
			start(dir, "view");
			try {
				if (getAt().isPresent()) {
					for (String place : getAt().get().split(";")) {
						int x = Integer.parseInt(place.split(",")[0].trim()), z = Integer.parseInt(place.split(",")[1].trim()), reach = 4;
						generate((x >> 4) - reach, (z >> 4) - reach, reach * 2 + 1, "rows", 0);
						Path dump = dir.resolve("towers").resolve("at-" + x + "_" + z + ".bin").toAbsolutePath();
						command("towertest tower " + x + " " + z + " " + dump, "[tower-check] DONE tower", 120);
					}
					continue;
				}
				List<Site> all = sites(dir);
				getLogger().lifecycle("    seed {}: {} sites in the square", seed, all.size());
				List<Site> picks = new ArrayList<>();
				List<String> climates = new ArrayList<>();
				for (Site s : all) {
					if (!climates.contains(s.climate)) {
						climates.add(s.climate);
						picks.add(s);
					}
				}
				for (Site s : all) if (!picks.contains(s)) picks.add(s);
				picks = picks.subList(0, Math.min(picks.size(), getTowers().get()));
				for (Site s : picks) {
					getLogger().lifecycle("    {}", s.line);
					int reach = 4;
					generate((s.x >> 4) - reach, (s.z >> 4) - reach, reach * 2 + 1, "rows", 0);
					Path dump = dir.resolve("towers").resolve(s.climate + "-" + s.x + "_" + s.z + ".bin").toAbsolutePath();
					command("towertest tower " + s.x + " " + s.z + " " + dump, "[tower-check] DONE tower", 120);
				}
			} finally {
				stop("seed " + seed);
			}
		}
	}

	/** Where each seed's sites would stand over a wide square of squares, at the margin in use and the first one, for the spacing between towers: {@code <run>/<seed>/places-<margin>.txt}. */
	private void spacing(Path run) throws Exception {
		for (long seed : getSeeds().get()) {
			Path dir = run.resolve(Long.toString(seed));
			serverDir(dir, seed, true, false);
			start(dir, "spacing");
			try {
				for (int margin : new int[] {19, 100, 112, 128}) {
					Path file = dir.resolve("places-" + margin + ".txt").toAbsolutePath();
					command("towertest places -24 -24 48 " + margin + " " + file, "[tower-check] DONE places", 900);
					getLogger().lifecycle("    seed {}, margin {}: {} sites", seed, margin, Files.readAllLines(file).size());
				}
			} finally {
				stop("seed " + seed);
			}
		}
	}

	/** What stood in one visiting order. */
	private Square square(Path run, long seed, String name, boolean towers, String order, String mode, boolean editions) throws Exception {
		Path dir = run.resolve(seed + "-" + name);
		serverDir(dir, seed, towers, editions);
		String label = seed + " " + name;
		long began = System.currentTimeMillis();
		int size = getSize().get();
		start(dir, "first");
		try {
			if (mode.equals("restart")) {
				generate(X0, Z0, size, order, size * size / 2);
				command("save-all", "Save complete.", 120);
				stop(label + ", first half");
				start(dir, "second");
			} else if (mode.equals("crash")) {
				generate(X0, Z0, size, order, size * size / 2);
				command("save-all", "Save complete.", 120);
				generate(X0, Z0, size, order, 0);
				server.send("towertest crash 6");
				Integer exit = server.waitFor(120);
				if (exit == null) failures.add(label + ": the server didn't go down");
				server.close();
				start(dir, "second");
			}
			generate(X0, Z0, size, order, 0);
			command("towertest cells " + X0 + " " + Z0 + " " + size + " " + dir.resolve("cells.bin").toAbsolutePath(), "[tower-check] DONE cells", 180);
			if (towers) reports(dir, label, true);
			if (towers && mode.equals("restart")) doors(dir, label);
		} finally {
			stop(label);
		}
		getLogger().lifecycle("    {}: {} s", label, (System.currentTimeMillis() - began) / 1000);
		return Square.read(dir.resolve("cells.bin"));
	}

	/** Each line of the driver's report on the square: every finished cell of every standing tower as planned, and every cell of a tower that isn't. */
	private void reports(Path dir, String label, boolean whole) throws Exception {
		Path file = dir.resolve("report-" + label.replace(' ', '-') + ".txt").toAbsolutePath();
		command("towertest report " + X0 + " " + Z0 + " " + getSize().get() + " " + file, "[tower-check] DONE report", 300);
		int standing = 0;
		for (String line : Files.readAllLines(file)) {
			getLogger().lifecycle("    {}: {}", label, line.length() > 200 ? line.substring(0, 200) : line);
			if (line.contains("stands=true")) {
				standing++;
				int wrong = Integer.parseInt(line.replaceAll(".* wrong=(\\d+).*", "$1"));
				// After a crash a chunk that's lost decorates again into its sealed neighbours, which are left alone as they may be the player's by then: a few cells of beta's veins in a wall.
				expect(label + ": as planned: " + (line.length() > 200 ? line.substring(0, 200) : line), wrong <= (label.contains("crash") ? 400 : 0));
			}
		}
		if (whole) expect(label + ": some towers stand: " + standing, standing > 0);
	}

	/** After a save and a restart, every iron door with a lever by it stands closed across its doorway, and its lever opens it out of the way, in the real server's own rules. */
	private void doors(Path dir, String label) throws Exception {
		Path file = dir.resolve("doors.txt").toAbsolutePath();
		command("towertest doors " + X0 + " " + Z0 + " " + getSize().get() + " " + file, "[tower-check] DONE doors", 300);
		List<String> lines = Files.readAllLines(file);
		long good = lines.stream().filter(l -> l.contains("before closed,across pulled open,aside back closed,across")).count();
		for (String line : lines) if (!line.contains("before closed,across pulled open,aside back closed,across")) getLogger().lifecycle("    {}: door {}", label, line);
		expect(label + ": iron doors start closed and their levers open them: " + good + " of " + lines.size(), good == lines.size() && good > 0);
	}

	/**
	 * An older world: a square made with no mod, then the mod put in and the land round it made. A tower that would cross what's already there isn't built, whenever and in whatever order its other
	 * chunks come, and the old chunks don't change.
	 */
	private void olderWorld(Path run, long seed) throws Exception {
		Path dir = run.resolve(seed + "-older");
		serverDir(dir, seed, false, false);
		int size = getSize().get(), old = size / 2;
		start(dir, "without the mod");
		try {
			generate(X0, Z0, old, "rows", 0);
			command("towertest cells " + X0 + " " + Z0 + " " + old + " " + dir.resolve("before.bin").toAbsolutePath(), "[tower-check] DONE cells", 180);
			command("save-all", "Save complete.", 120);
		} finally {
			stop(seed + " older, without the mod");
		}
		for (File mod : getTowerMod().getFiles()) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()), StandardCopyOption.REPLACE_EXISTING);
		start(dir, "with the mod");
		try {
			generate(X0, Z0, size, "shuffle" + seed, 0);
			command("towertest cells " + X0 + " " + Z0 + " " + old + " " + dir.resolve("after.bin").toAbsolutePath(), "[tower-check] DONE cells", 180);
			reports(dir, seed + " older world", false);
			command("save-all", "Save complete.", 120);
		} finally {
			stop(seed + " older, with the mod");
		}
		start(dir, "again");
		try {
			reports(dir, seed + " older world, reopened", false);
		} finally {
			stop(seed + " older, again");
		}
		Square before = Square.read(dir.resolve("before.bin")), after = Square.read(dir.resolve("after.bin"));
		long moved = 0;
		String sample = "";
		// Its last row and column of chunks still had decoration to come when the mod arrived.
		for (int cx = 0; cx < old - 1; cx++) {
			for (int cz = 0; cz < old - 1; cz++) {
				int c = cx * old + cz;
				if (!before.populated[c]) continue;
				for (int i = 0; i < CELLS; i++) {
					int p = before.ids[c][i] & 0xFF, q = after.ids[c][i] & 0xFF;
					if (p != q && !(fluid(p) && (fluid(q) || flowMade(q) || falling(q))) && !(fluid(q) && (flowMade(p) || falling(p)))) {
						moved++;
						if (sample.length() < 100) sample += " " + i + ":" + p + ">" + q;
					}
				}
			}
		}
		expect(seed + ": the older chunks are as they were (" + moved + " cells differ" + sample + ")", moved == 0);
		// Every site that reaches the old square has to have stayed out; the report said so of each.
		boolean crossed = false, kept = false;
		for (String line : Files.readAllLines(dir.resolve("report-" + seed + "-older-world.txt"))) {
			String[] p = line.split(" ");
			int x = Integer.parseInt(p[0]), z = Integer.parseInt(p[1]);
			int x0 = x - TOWER_REACH >> 4, x1 = x + TOWER_REACH >> 4, z0 = z - TOWER_REACH >> 4, z1 = z + TOWER_REACH >> 4;
			boolean crosses = x0 <= X0 + old && z0 <= Z0 + old && x1 >= X0 && z1 >= Z0;
			boolean stands = line.contains("stands=true");
			if (x0 < X0 + old - 1 && z0 < Z0 + old - 1 && x1 >= X0 && z1 >= Z0) expect(seed + ": a site in the old square doesn't stand: " + p[0] + "," + p[1], !stands);
			crossed |= crosses && !stands;
			kept |= !crosses && stands;
		}
		getLogger().lifecycle("    seed {}: older world: a tower held back {}, one built past it {}", seed, crossed, kept);
	}

	/** A chunk of a tower lost from the save comes back as part of the tower, and what a player did to a chunk beside it stays. */
	private void lostChunk(Path run, long seed) throws Exception {
		Path dir = run.resolve(seed + "-lost");
		serverDir(dir, seed, true, false);
		start(dir, "first");
		List<Site> sites = sites(dir);
		Site site = sites.isEmpty() ? null : sites.get(0);
		if (site == null) {
			stop(seed + " lost");
			return;
		}
		int cx = site.x >> 4, cz = site.z >> 4, reach = 5;
		int markX = (cx + 1) * 16 + 2, markZ = cz * 16 + 2, markY = 120;
		try {
			generate(cx - reach, cz - reach, reach * 2 + 1, "rows", 0);
			// A player's cobble high in the air, over a chunk that isn't the one lost.
			command("towertest set " + markX + " " + markY + " " + markZ + " 4", "[tower-check] DONE set", 30);
			command("save-all", "Save complete.", 120);
		} finally {
			stop(seed + " lost, first");
		}
		zeroChunk(dir.resolve("world/region"), cx, cz);
		start(dir, "second");
		try {
			generate(cx - reach, cz - reach, reach * 2 + 1, "rows", 0);
			Path file = dir.resolve("report-lost.txt").toAbsolutePath();
			command("towertest report " + (cx - reach) + " " + (cz - reach) + " " + (reach * 2 + 1) + " " + file, "[tower-check] DONE report", 120);
			for (String line : Files.readAllLines(file)) {
				if (!line.startsWith(site.x + " " + site.z + " ")) continue;
				getLogger().lifecycle("    lost: {}", line.length() > 200 ? line.substring(0, 200) : line);
				// A neighbour's sealed cells can't be restored once the chunk's decoration runs into them again, so a few cells may be vanilla's; none should be missing from the lost chunk's own column of cells.
				int wrong = Integer.parseInt(line.replaceAll(".* wrong=(\\d+).*", "$1"));
				expect(seed + ": a lost chunk came back with its tower, " + wrong + " cells off", line.contains("stands=true") && wrong < 800);
			}
			int from = server.lineCount();
			server.send("towertest get " + markX + " " + markY + " " + markZ);
			String got = server.awaitFrom(from, l -> l.contains("[tower-check] DONE get"), 30);
			expect(seed + ": a player's block in the chunk beside it is still there: " + got, got != null && got.trim().endsWith(" 4"));
		} finally {
			stop(seed + " lost, second");
		}
	}

	/** Takes one chunk out of a region's table, as a damaged save loses it. */
	private static void zeroChunk(Path regions, int cx, int cz) throws IOException {
		Path file = regions.resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mcr");
		try (java.io.RandomAccessFile region = new java.io.RandomAccessFile(file.toFile(), "rw")) {
			region.seek(4L * ((cx & 31) + (cz & 31) * 32));
			region.writeInt(0);
		}
	}

	private static final int SPAWNER = 52, CHEST = 54;

	private static boolean fluid(int id) {
		return id == 0 || id >= 8 && id <= 11;
	}

	private static boolean flowMade(int id) {
		return id == 1 || id == 4 || id == 49 || id >= 37 && id <= 40 || id == 31 || id == 32;
	}

	private static boolean falling(int id) {
		return id == 12 || id == 13;
	}

	/** Cells differing between two squares, apart from what flows and falls by the world's own unseeded chance; the second number is those. */
	private long[] difference(Square a, Square b, List<String> samples) {
		long solid = 0, flow = 0;
		for (int c = 0; c < a.ids.length; c++) {
			if (!a.populated[c] || !b.populated[c]) continue;
			int cx = c / a.size + a.x0, cz = c % a.size + a.z0;
			for (int i = 0; i < CELLS; i++) {
				int p = a.ids[c][i] & 0xFF, q = b.ids[c][i] & 0xFF;
				if (p == q) continue;
				int x = cx * 16 + (i >> 11), z = cz * 16 + (i >> 7 & 15), y = i & 127;
				boolean moved = fluid(p) && (fluid(q) || flowMade(q) || falling(q)) || fluid(q) && (flowMade(p) || falling(p));
				if (moved) {
					flow++;
				} else {
					solid++;
					if (samples.size() < 12) samples.add(x + "," + y + "," + z + ": " + p + " against " + q);
				}
			}
		}
		return new long[] {solid, flow};
	}

	private static int count(Square s, int id) {
		int n = 0;
		for (byte[] cells : s.ids) {
			for (byte c : cells) if ((c & 0xFF) == id) n++;
		}
		return n;
	}

	/**
	 * Ores with the mod against without it, chunk by chunk: chunks a tower has nothing to do with should be cell for cell beta's, but for the rooms of dungeons that are no longer there. Which chunks are
	 * different and how, for a look; a chunk with its veins moved is the failure.
	 */
	private void veinsOutside(Square plain, Square with, long seed) throws IOException {
		int same = 0, roomsOnly = 0, moved = 0;
		StringBuilder where = new StringBuilder();
		for (int c = 0; c < plain.ids.length; c++) {
			if (!plain.populated[c] || !with.populated[c]) continue;
			int ores = 0, others = 0;
			for (int i = 0; i < CELLS; i++) {
				int p = plain.ids[c][i] & 0xFF, q = with.ids[c][i] & 0xFF;
				if (p == q) continue;
				boolean ore = p >= 14 && p <= 16 || q >= 14 && q <= 16 || p == 21 || q == 21 || p == 56 || q == 56 || p == 73 || q == 73;
				if (ore) ores++;
				else others++;
			}
			if (ores == 0 && others == 0) same++;
			else if (ores < 12) roomsOnly++;
			else {
				moved++;
				if (where.length() < 200) where.append(' ').append(c / plain.size + plain.x0).append(',').append(c % plain.size + plain.z0).append(':').append(ores);
			}
		}
		getLogger().lifecycle("    seed {}: chunks identical {}, a few ore cells {}, veins moved {}{}", seed, same, roomsOnly, moved, where);
		lines.add("seed " + seed + " chunks identical " + same + ", a few ore cells " + roomsOnly + ", veins moved " + moved + where);
	}

	private void check(Path run) throws Exception {
		int size = getSize().get();
		for (long seed : getSeeds().get()) {
			Square plain = square(run, seed, "plain", false, "rows", "", false);
			Square rows = square(run, seed, "rows", true, "rows", "", false);
			// Each held against its plan as it's made, and read back only for the record: beta's own world differs by order.
			square(run, seed, "rings", true, "rings", "", false);
			square(run, seed, "shuffled", true, "shuffle" + seed, "restart", false);
			square(run, seed, "back", true, "back", "", true);
			square(run, seed, "crash", true, "shuffle" + (seed + 1), "crash", false);
			List<String> samples = new ArrayList<>();
			getLogger().lifecycle("    seed {}: {} spawners without the mod, {} with it", seed, count(plain, SPAWNER), count(rows, SPAWNER));
			// Every ore and rock outside a tower's reach as it is without the mod; a vetoed dungeon's own cells are what's meant to differ.
			samples.clear();
			long[] d = difference(plain, rows, samples);
			getLogger().lifecycle("    seed {}, with and without the mod over the whole square: {} solid cells, {} by flow; {}", seed, d[0], d[1], samples);
			veinsOutside(plain, rows, seed);
			olderWorld(run, seed);
			lostChunk(run, seed);
		}
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		if (getMode().get().equals("view")) view(run);
		else if (getMode().get().equals("spacing")) spacing(run);
		else check(run);
		Files.write(run.resolve("harness-output.log"), lines);
		if (!failures.isEmpty()) throw new GradleException("Buried Towers' test failed:\n" + String.join("\n", failures));
	}
}
