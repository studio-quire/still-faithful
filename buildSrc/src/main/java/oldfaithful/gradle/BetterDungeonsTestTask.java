package oldfaithful.gradle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/**
 * Better Dungeons on really generated worlds (docs/library.md). Each seed's square of chunks, far from the spawn, is made by the real server through library/dungeon-check, with and without the mod,
 * in several visiting orders, one of them across a restart. Then, from what was really in the world: every dungeon the same whatever the order, every room reachable from beta's, nothing open to
 * anything but its own rooms, and every cell outside the dungeons as a world without the mod has it.
 */
public abstract class BetterDungeonsTestTask extends ClientStartupTask {
	private static final int CELLS = 32768;
	/** Far enough from the spawn that its chunks, made first in every run, are nowhere near. */
	private static final int X0 = 48, Z0 = 48;
	private static final byte NONE = 0, CEILING = 1, COBBLE = 2, MOSS = 3, AIR = 4, GRAVEL = 5, WATER = 6, WEB = 7, SPAWNER = 8, CHEST = 9;

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
	public abstract ConfigurableFileCollection getDungeonMod();

	/** The editions' server mods, for the run that puts them all under it. */
	@InputFiles
	@PathSensitive(PathSensitivity.NAME_ONLY)
	public abstract ConfigurableFileCollection getEditionMods();

	@Input
	public abstract ListProperty<Long> getSeeds();

	@Input
	public abstract Property<Integer> getSize();

	private final List<String> lines = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private GameProcess server;

	private static final class Plan {
		int x, y, z, halfX, halfZ;
		int[][] rooms;
		int[][] halls;
		final Map<Long, Byte> cells = new TreeMap<>();
		final Map<Long, String> contents = new TreeMap<>();

		boolean inBeta(int cx, int cy, int cz) {
			return Math.abs(cx - x) <= halfX + 1 && Math.abs(cz - z) <= halfZ + 1 && cy >= y - 1 && cy <= y + 4;
		}

		String print() {
			return x + "," + y + "," + z + " " + Arrays.deepToString(rooms) + " " + Arrays.deepToString(halls) + " " + cells.hashCode() + " " + contents;
		}
	}

	/** A run's square as it stood. */
	private static final class Dump {
		int x0, z0, size;
		byte[][] blocks;
		boolean[] populated;
		final List<int[]> placed = new ArrayList<>();
		final Map<String, Plan> plans = new LinkedHashMap<>();

		int block(int x, int y, int z) {
			if (y < 0 || y >= 128) return 0;
			int cx = (x >> 4) - x0, cz = (z >> 4) - z0;
			if (cx < 0 || cz < 0 || cx >= size || cz >= size) return -1;
			return blocks[cx * size + cz][(x & 15) << 11 | (z & 15) << 7 | y] & 0xFF;
		}

		static Dump read(Path file) throws IOException {
			Dump d = new Dump();
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
				d.x0 = in.readInt();
				d.z0 = in.readInt();
				d.size = in.readInt();
				d.blocks = new byte[d.size * d.size][CELLS];
				d.populated = new boolean[d.size * d.size];
				for (int c = 0; c < d.blocks.length; c++) {
					d.populated[c] = in.readBoolean();
					in.readFully(d.blocks[c]);
				}
				int placed = in.readInt();
				for (int i = 0; i < placed; i++) d.placed.add(new int[] {in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt()});
				int plans = in.readInt();
				for (int i = 0; i < plans; i++) {
					Plan p = new Plan();
					p.x = in.readInt();
					p.y = in.readInt();
					p.z = in.readInt();
					p.halfX = in.readInt();
					p.halfZ = in.readInt();
					p.rooms = new int[in.readInt()][6];
					p.halls = new int[in.readInt()][];
					for (int[] room : p.rooms) {
						for (int j = 0; j < 6; j++) room[j] = in.readInt();
					}
					for (int h = 0; h < p.halls.length; h++) {
						int from = in.readInt(), to = in.readInt(), length = in.readInt();
						p.halls[h] = new int[length + 2];
						p.halls[h][0] = from;
						p.halls[h][1] = to;
						for (int j = 0; j < length; j++) p.halls[h][j + 2] = in.readInt();
					}
					int cells = in.readInt();
					for (int c = 0; c < cells; c++) {
						int x = in.readInt(), y = in.readInt(), z = in.readInt();
						byte kind = in.readByte();
						String contents = in.readUTF();
						p.cells.put(key(x, y, z), kind);
						if (!contents.isEmpty()) p.contents.put(key(x, y, z), contents);
					}
					d.plans.put(p.x + "," + p.y + "," + p.z, p);
				}
			}
			return d;
		}
	}

	private static long key(int x, int y, int z) {
		return (long) (x + (1 << 25)) << 34 | (long) (z + (1 << 25)) << 8 | y;
	}

	private static int keyX(long key) {
		return (int) (key >>> 34) - (1 << 25);
	}

	private static int keyZ(long key) {
		return (int) (key >>> 8 & (1L << 26) - 1) - (1 << 25);
	}

	private static int keyY(long key) {
		return (int) (key & 0xFF);
	}

	private void expect(String what, boolean ok) {
		getLogger().lifecycle("{}: {}", ok ? "ok  " : "FAIL", what);
		if (!ok) failures.add(what);
	}

	private void await(String marker, int seconds) throws Exception {
		if (server.await(l -> l.contains(marker), seconds) == null) throw new IOException("no \"" + marker + "\" within " + seconds + " s");
	}

	private void awaitAfter(int from, String marker, int seconds) throws Exception {
		if (server.awaitFrom(from, l -> l.contains(marker), seconds) == null) throw new IOException("no \"" + marker + "\" within " + seconds + " s");
	}

	private void start(Path dir, String label) throws Exception {
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		List<String> command = windows ? List.of("cmd", "/c", dir.resolve("start.bat").toString()) : List.of("sh", dir.resolve("start.sh").toString());
		server = new GameProcess(command, dir, dir.resolve("harness-output-" + label + ".log"), Map.of("JAVA", getJavaExecutable().get(), "JAVA_OPTS", "-Xmx1200m"));
		await("Done (", 300);
	}

	private void stop(String label) throws Exception {
		server.send("stop");
		Integer exit = server.waitFor(120);
		if (exit == null) failures.add(label + ": the server didn't stop within 2 minutes");
		List<String> out = server.lines();
		server.close();
		for (String p : problems(out)) failures.add(label + ": " + p);
		for (String line : out) {
			if (line.contains("[faithful-core] WARN")) failures.add(label + ": " + line);
		}
		lines.add("--- " + label);
		lines.addAll(out);
	}

	/** Loads the square, or the first {@code count} of it, and waits for the world to go quiet. The line that says how many are still waiting. */
	private String generate(String order, int count) throws Exception {
		int from = server.lineCount();
		server.send("dungeontest gen " + X0 + " " + Z0 + " " + getSize().get() + " " + order + " 8" + (count > 0 ? " " + count : ""));
		awaitAfter(from, "[dungeon-check] DONE gen", 600);
		from = server.lineCount();
		server.send("dungeontest settle");
		return server.awaitFrom(from, l -> l.contains("[dungeon-check] DONE settle"), 300);
	}

	private int serverDir(Path dir, long seed, boolean grow, boolean editions, int view) throws IOException {
		copyTree(getServerPackage().get().getAsFile().toPath(), dir);
		Files.copy(getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
		List<File> mods = new ArrayList<>(getServerMods().getFiles());
		if (grow) mods.addAll(getDungeonMod().getFiles());
		if (editions) mods.addAll(getEditionMods().getFiles());
		for (File mod : mods) Files.copy(mod.toPath(), dir.resolve("mods").resolve(mod.getName()), StandardCopyOption.REPLACE_EXISTING);
		int port;
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=" + view,
				"max-players=20", "spawn-monsters=false", "spawn-animals=false", "allow-nether=false", "level-seed=" + seed, ""));
		return port;
	}

	private void saveAll() throws Exception {
		int from = server.lineCount();
		server.send("save-all");
		awaitAfter(from, "Save complete.", 120);
	}

	private void crashed(String label) throws Exception {
		Integer exit = server.waitFor(120);
		if (exit == null) failures.add(label + ": the server didn't go down");
		lines.add("--- " + label + ", up to the crash");
		lines.addAll(server.lines());
		server.close();
	}

	private Dump world(Path run, long seed, String name, boolean grow, String order, boolean restart, boolean editions) throws Exception {
		return world(run, seed, name, grow, order, restart ? "restart" : "", editions);
	}

	private Dump world(Path run, long seed, String name, boolean grow, String order, String mode, boolean editions) throws Exception {
		Path dir = run.resolve(seed + "-" + name);
		serverDir(dir, seed, grow, editions, 3);
		String label = seed + " " + name;
		long began = System.currentTimeMillis();
		start(dir, "first");
		try {
			int size = getSize().get();
			if (mode.equals("restart")) {
				getLogger().lifecycle("    {}, half the chunks: {}", label, generate(order, size * size / 2));
				saveAll();
				stop(label + ", first half");
				start(dir, "second");
			} else if (mode.equals("crash")) {
				// Half saved with dungeons still waiting; then the rest made and grown, and only some of what changed written before the end.
				getLogger().lifecycle("    {}, half the chunks: {}", label, generate(order, size * size / 2));
				saveAll();
				getLogger().lifecycle("    {}, all of them: {}", label, generate(order, 0));
				server.send("dungeontest crash 6");
				crashed(label);
				start(dir, "second");
			} else if (mode.equals("revert")) {
				generate(order, 0);
				int from = server.lineCount();
				server.send("dungeontest revert");
				awaitAfter(from, "[dungeon-check] DONE revert", 60);
				getLogger().lifecycle("    {}: {}", label, server.lines().get(server.lines().size() - 1));
				saveAll();
				stop(label + ", first run");
				start(dir, "second");
			}
			String waiting = generate(order, 0);
			getLogger().lifecycle("    {}: {}", label, waiting);
			int from = server.lineCount();
			server.send("dungeontest dump " + X0 + " " + Z0 + " " + getSize().get() + " " + dir.resolve("dump.bin").toAbsolutePath());
			awaitAfter(from, "[dungeon-check] DONE dump", 120);
			from = server.lineCount();
			server.send("save-all");
			awaitAfter(from, "Save complete.", 120);
		} finally {
			stop(label);
		}
		Dump dump = Dump.read(dir.resolve("dump.bin"));
		getLogger().lifecycle("    {}: {} dungeons placed, {} grown, {} s", label, dump.placed.size(), dump.plans.size(), (System.currentTimeMillis() - began) / 1000);
		return dump;
	}

	private static boolean fluid(int id) {
		return id == 0 || id >= 8 && id <= 11;
	}

	/** What lava and water make where they meet, and the plants they wash away: it goes with the flow. */
	private static boolean flowMade(int id) {
		return id == 1 || id == 4 || id == 49 || id >= 37 && id <= 40;
	}

	private static boolean falling(int id) {
		return id == 12 || id == 13;
	}

	private static boolean beside(Set<Long> cells, int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (cells.contains(key(x + dx, y + dy, z + dz))) return true;
				}
			}
		}
		return false;
	}

	/**
	 * Cells differing between two worlds, outside {@code except}. Lava and water flow by the world's own unseeded chance, and what they touch falls, so what's air, liquid or fallen is counted apart, as
	 * two runs of the same world differ that way too: unless it's by a dungeon, where the mod could have had a hand in it.
	 */
	private long[] difference(Dump a, Dump b, Set<Long> except, List<String> samples) {
		long solid = 0, flow = 0;
		for (int c = 0; c < a.blocks.length; c++) {
			if (!a.populated[c] || !b.populated[c]) continue;
			int cx = c / a.size + a.x0, cz = c % a.size + a.z0;
			for (int i = 0; i < CELLS; i++) {
				int p = a.blocks[c][i] & 0xFF, q = b.blocks[c][i] & 0xFF;
				if (p == q) continue;
				int x = cx * 16 + (i >> 11), z = cz * 16 + (i >> 7 & 15), y = i & 127;
				if (except != null && except.contains(key(x, y, z))) continue;
				boolean moved = fluid(p) && (fluid(q) || flowMade(q) || falling(q)) || fluid(q) && (flowMade(p) || falling(p));
				if (moved && (except == null || !beside(except, x, y, z))) {
					flow++;
				} else {
					solid++;
					if (samples.size() < 12) samples.add(x + "," + y + "," + z + ": " + p + " against " + q);
				}
			}
		}
		return new long[] {solid, flow};
	}

	private static Set<Long> footprint(Dump d) {
		Set<Long> cells = new HashSet<>();
		for (Plan p : d.plans.values()) cells.addAll(p.cells.keySet());
		return cells;
	}

	private static int rank(int id, boolean theirs) {
		switch (id) {
			case 0: return AIR;
			case 4: return COBBLE;
			case 48: return MOSS;
			case 13: return theirs ? GRAVEL : NONE;
			case 9: return WATER;
			case 30: return WEB;
			case 52: return SPAWNER;
			case 54: return CHEST;
			default: return NONE;
		}
	}

	private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

	private static boolean inside(byte kind) {
		return kind >= AIR;
	}

	/** What really stands in the world for one dungeon. Anything wrong goes in {@code wrong}. */
	private void structure(Dump d, Plan p, Set<Long> others, List<String> wrong) {
		String name = "dungeon at " + p.x + "," + p.y + "," + p.z;
		// Every cell as painted, or outranked by a dungeon that shares it.
		for (Map.Entry<Long, Byte> e : p.cells.entrySet()) {
			int x = keyX(e.getKey()), y = keyY(e.getKey()), z = keyZ(e.getKey()), id = d.block(x, y, z);
			byte kind = e.getValue();
			if (id < 0) {
				wrong.add(name + ": a cell past the square at " + x + "," + y + "," + z);
			} else if (kind == CEILING ? !others.contains(e.getKey()) && (fluid(id) || id == 13 || id == 12) : rank(id, kind == GRAVEL || others.contains(e.getKey())) < kind) {
				wrong.add(name + ": at " + x + "," + y + "," + z + " a cell of kind " + kind + " is block " + id);
			}
		}
		// Nothing inside opens on anything but the dungeon, beta's room by its doors, or a dungeon that grew into it.
		Set<Long> visited = new HashSet<>();
		Deque<Long> queue = new ArrayDeque<>();
		for (Map.Entry<Long, Byte> e : p.cells.entrySet()) {
			int x = keyX(e.getKey()), y = keyY(e.getKey()), z = keyZ(e.getKey());
			if (!inside(e.getValue())) continue;
			boolean door = p.inBeta(x, y, z);
			if (door) {
				queue.add(e.getKey());
				visited.add(e.getKey());
			}
			for (int[] s : SIDES) {
				int nx = x + s[0], ny = y + s[1], nz = z + s[2];
				long n = key(nx, ny, nz);
				Byte neighbour = p.cells.get(n);
				if (neighbour != null && inside(neighbour) || p.inBeta(nx, ny, nz) || others.contains(n)) continue;
				int id = d.block(nx, ny, nz);
				if (id < 0 || fluid(id) || id == 30) wrong.add(name + ": open at " + x + "," + y + "," + z + " to block " + id + " at " + nx + "," + ny + "," + nz);
			}
		}
		// Every room reachable from beta's, on foot, swimming or digging through a fallen corridor.
		while (!queue.isEmpty()) {
			long cell = queue.remove();
			int x = keyX(cell), y = keyY(cell), z = keyZ(cell);
			for (int[] s : SIDES) {
				long n = key(x + s[0], y + s[1], z + s[2]);
				Byte kind = p.cells.get(n);
				if (kind == null || !inside(kind) || kind >= SPAWNER || !visited.add(n)) continue;
				queue.add(n);
			}
		}
		for (Map.Entry<Long, Byte> e : p.cells.entrySet()) {
			byte kind = e.getValue();
			if (inside(kind) && kind < SPAWNER && !visited.contains(e.getKey()) && !others.contains(e.getKey()))
				wrong.add(name + ": can't be reached at " + keyX(e.getKey()) + "," + keyY(e.getKey()) + "," + keyZ(e.getKey()));
		}
		for (Map.Entry<Long, String> e : p.contents.entrySet()) {
			if (e.getValue().equals("none")) wrong.add(name + ": no block entity at " + keyX(e.getKey()) + "," + keyY(e.getKey()) + "," + keyZ(e.getKey()));
		}
	}

	private void checkStructures(String label, Dump d) {
		Map<Long, Integer> sharing = new HashMap<>();
		for (Plan p : d.plans.values()) {
			for (long cell : p.cells.keySet()) sharing.merge(cell, 1, Integer::sum);
		}
		List<String> wrong = new ArrayList<>();
		int merged = 0;
		for (Plan p : d.plans.values()) {
			Set<Long> others = new HashSet<>();
			for (long cell : p.cells.keySet()) {
				if (sharing.get(cell) > 1) others.add(cell);
			}
			if (!others.isEmpty()) merged++;
			structure(d, p, others, wrong);
		}
		for (String w : wrong.subList(0, Math.min(10, wrong.size()))) getLogger().lifecycle("    {}", w);
		expect(label + ": " + d.plans.size() + " grown dungeons stand as planned, sealed and whole (" + merged + " reach another)" + (wrong.isEmpty() ? "" : "; " + wrong.size() + " faults"), wrong.isEmpty());
	}

	private static boolean open(int id) {
		return fluid(id) || id == 12 || id == 13;
	}

	/** Whether the world without the mod, in these two orders, differs within a dungeon's reach in what's open and what's rock: vanilla's own decoration overlaps by order, and then the two dungeons had different ground to go on. */
	private static boolean vanillaDiffers(Dump a, Dump b, int x0, int y0, int z0) {
		for (int x = x0 - 30; x <= x0 + 30; x++) {
			for (int z = z0 - 30; z <= z0 + 30; z++) {
				for (int y = y0 - 5; y <= y0 + 6; y++) {
					int p = a.block(x, y, z), q = b.block(x, y, z);
					if (p != q && open(p) != open(q)) return true;
				}
			}
		}
		return false;
	}

	/** Two orders, with the two plain worlds made in those orders. A dungeon may differ only where vanilla's did. */
	private void compareOrders(String label, Dump a, Dump b, Dump plainA, Dump plainB) {
		Set<String> keys = new HashSet<>(a.plans.keySet());
		keys.addAll(b.plans.keySet());
		int differ = 0, excused = 0;
		Set<Long> cells = new HashSet<>();
		for (String key : keys) {
			Plan p = a.plans.get(key), q = b.plans.get(key);
			if (p != null && q != null && p.print().equals(q.print())) {
				cells.addAll(p.cells.keySet());
				continue;
			}
			Plan either = p != null ? p : q;
			if (vanillaDiffers(plainA, plainB, either.x, either.y, either.z)) {
				excused++;
			} else if (differ++ < 3) {
				getLogger().lifecycle("    differs:\n      {}\n      {}", p == null ? "none" : p.print(), q == null ? "none" : q.print());
			}
		}
		expect(label + ": the same dungeons, cell for cell and chest for chest: " + keys.size() + ", " + excused + " apart where beta's own rock differs between the orders, " + differ + " otherwise", differ == 0);
		long different = 0;
		for (long cell : cells) {
			int x = keyX(cell), y = keyY(cell), z = keyZ(cell), s = a.block(x, y, z), t = b.block(x, y, z);
			if (s == t || fluid(s) && fluid(t)) continue;
			boolean ceiling = false;
			for (Plan p : a.plans.values()) {
				Byte kind = p.cells.get(cell);
				if (kind != null && kind != CEILING) {
					ceiling = false;
					break;
				}
				if (kind != null) ceiling = true;
			}
			// A ceiling is whatever the rock was, and beta's veins overlap by order.
			if (!ceiling) different++;
		}
		expect(label + ": what stands in them is the same, " + different + " cells of " + cells.size() + " differ", different == 0);
	}

	private void statistics(long seed, Dump d) {
		int populatedSites = inSquare(d).size();
		Map<Integer, Integer> sizes = new TreeMap<>();
		int rooms = 0, spawners = 0, chests = 0, webRooms = 0, sunk = 0, gravelHalls = 0;
		Map<Integer, int[]> byDepth = new TreeMap<>();
		for (Plan p : d.plans.values()) {
			sizes.merge(p.rooms.length, 1, Integer::sum);
			rooms += p.rooms.length;
			spawners++;
			boolean webs = false, gravel = false;
			for (int r = 1; r < p.rooms.length; r++) {
				int[] room = p.rooms[r];
				if (room[4] > 0) sunk++;
				int[] tally = byDepth.computeIfAbsent(room[5], k -> new int[2]);
				tally[0]++;
				for (Map.Entry<Long, Byte> e : p.cells.entrySet()) {
					int x = keyX(e.getKey()), z = keyZ(e.getKey());
					if (x < room[0] || x > room[2] || z < room[1] || z > room[3]) continue;
					if (e.getValue() == CHEST) tally[1]++;
				}
			}
			for (byte kind : p.cells.values()) {
				if (kind == SPAWNER) spawners++;
				if (kind == CHEST) chests++;
				if (kind == WEB) webs = true;
				if (kind == GRAVEL) gravel = true;
			}
			if (webs) webRooms++;
			if (gravel) gravelHalls++;
		}
		StringBuilder depths = new StringBuilder();
		byDepth.forEach((k, v) -> depths.append(" depth ").append(k).append(": ").append(v[1]).append(" in ").append(v[0]));
		getLogger().lifecycle("  seed {}: {} placed, {} grown; rooms {}; {} spawners in {} rooms; {} chests beyond beta's ({}); webs in {}, flooded {}, fallen corridors {}", seed, populatedSites, d.plans.size(), sizes,
				spawners, rooms, chests, depths.toString().trim(), webRooms, sunk, gravelHalls);
	}

	/** A client with nothing of the library, joined to a server that's grown what's round its spawn: it must play, and have the same cells. */
	private void plainClient(Path run, long seed) throws Exception {
		Path dir = run.resolve("plain-client-server");
		int port = serverDir(dir, seed, true, false, 10);
		start(dir, "first");
		try {
			int from = server.lineCount();
			server.send("dungeontest settle");
			awaitAfter(from, "[dungeon-check] DONE settle", 300);
			from = server.lineCount();
			server.send("dungeontest spawn");
			awaitAfter(from, "[dungeon-check] DONE spawn", 30);
			String[] spawn = server.lines().stream().skip(from).filter(l -> l.contains("DONE spawn")).findFirst().get().split(" ");
			int spawnX = Integer.parseInt(spawn[spawn.length - 2]), spawnZ = Integer.parseInt(spawn[spawn.length - 1]);
			from = server.lineCount();
			server.send("dungeontest dump " + ((spawnX >> 4) - 12) + " " + ((spawnZ >> 4) - 12) + " 25 " + dir.resolve("dump.bin").toAbsolutePath());
			awaitAfter(from, "[dungeon-check] DONE dump", 120);
			Dump d = Dump.read(dir.resolve("dump.bin"));
			Plan near = null;
			int nearest = 8 * 16;
			for (Plan p : d.plans.values()) {
				int distance = Math.max(Math.abs(p.x - spawnX), Math.abs(p.z - spawnZ));
				if (distance < nearest) {
					nearest = distance;
					near = p;
				}
			}
			expect("a grown dungeon near the spawn of " + seed + ": " + (near == null ? "none" : near.x + "," + near.y + "," + near.z), near != null);
			if (near == null) return;
			StringBuilder cells = new StringBuilder();
			int every = Math.max(1, near.cells.size() / 40), i = 0;
			for (long cell : near.cells.keySet()) {
				if (i++ % every == 0) {
					int x = keyX(cell), y = keyY(cell), z = keyZ(cell);
					cells.append(cells.length() == 0 ? "" : ";").append(x + "," + y + "," + z + "," + d.block(x, y, z));
				}
			}
			Path game = run.resolve("plain-client");
			Files.createDirectories(game.resolve("mods"));
			for (File mod : getMods().getFiles()) Files.copy(mod.toPath(), game.resolve("mods").resolve(mod.getName()));
			prepare(game, false);
			lines.add("--- plain client");
			List<String> played = launch(game, false, List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + port, "-Doldfaithful.selftest.joinWorldChecks=true",
					"-Doldfaithful.selftest.expectBlocks=" + cells), failures);
			lines.addAll(played);
			expect("a plain client played on the server and has the dungeon's cells", played.stream().anyMatch(l -> l.contains("the client has what the server built")));
		} finally {
			stop("plain client's server");
		}
	}

	@TaskAction
	@Override
	public void run() throws Exception {
		Path run = freshRunDir();
		plainClient(run, getSeeds().get().get(0));
		for (long seed : getSeeds().get()) {
			Dump plainRows = world(run, seed, "plain-rows", false, "rows", false, false);
			Dump plainAgain = world(run, seed, "plain-rows-again", false, "rows", false, false);
			Dump plainRings = world(run, seed, "plain-rings", false, "rings", false, false);
			Dump plainShuffled = world(run, seed, "plain-shuffled", false, "shuffle" + seed, true, false);
			Dump plainBack = world(run, seed, "plain-back", false, "back", false, false);
			Dump rows = world(run, seed, "rows", true, "rows", false, false);
			Dump rings = world(run, seed, "rings", true, "rings", false, false);
			Dump shuffled = world(run, seed, "shuffled", true, "shuffle" + seed, true, false);
			Dump back = world(run, seed, "back-editions", true, "back", false, true);
			Dump crash = world(run, seed, "crash", true, "shuffle" + seed, "crash", false);
			Dump reverted = world(run, seed, "reverted", true, "rows", "revert", false);

			expect(seed + ": beta placed the same dungeons in the same order with the mod as without it: " + inSquare(rows).size(), deepEquals(inSquare(plainRows), inSquare(rows)));
			List<String> samples = new ArrayList<>();
			long[] again = difference(plainRows, plainAgain, null, samples);
			long[] baseline = difference(plainRows, plainRings, null, new ArrayList<>());
			getLogger().lifecycle("  seed {}: without the mod, one order twice differs in {} solid cells and {} moved by flow; two orders in {} solid cells and {} moved", seed, again[0], again[1], baseline[0], baseline[1]);
			expect(seed + ": a world without the mod, made twice in one order, differs only in what flowed: " + again[0] + " " + samples, again[0] == 0);
			samples.clear();
			long[] outside = difference(plainRows, rows, footprint(rows), samples);
			expect(seed + ": outside the dungeons, the world is the one without the mod: " + outside[0] + " cells differ, besides " + outside[1] + " moved by flow, none by a dungeon " + samples, outside[0] == 0);
			compareOrders(seed + ": rows against rings", rows, rings, plainRows, plainRings);
			compareOrders(seed + ": rows against shuffled across a restart", rows, shuffled, plainRows, plainShuffled);
			compareOrders(seed + ": rows against backwards with every edition's mods", rows, back, plainRows, plainBack);
			compareOrders(seed + ": rows against a crash partway through saving", rows, crash, plainRows, plainShuffled);
			compareOrders(seed + ": rows against a chunk of each dungeon put back, then restarted", rows, reverted, plainRows, plainRows);
			checkStructures(seed + ": a crash partway through saving", crash);
			checkStructures(seed + ": a chunk of each dungeon put back, then restarted", reverted);
			checkStructures(seed + ": rows", rows);
			checkStructures(seed + ": shuffled across a restart", shuffled);
			statistics(seed, rows);
			expect(seed + ": dungeons did grow", rows.plans.size() > 5);
			removeWorlds(run, seed);
		}
		report("Better Dungeons test", lines, failures);
	}

	/** The spawn moves from run to run, so what's placed round it does too. */
	private static List<int[]> inSquare(Dump d) {
		List<int[]> sites = new ArrayList<>();
		for (int[] site : d.placed) {
			int cx = (site[0] >> 4) - d.x0, cz = (site[2] >> 4) - d.z0;
			if (cx >= 0 && cz >= 0 && cx < d.size && cz < d.size) sites.add(site);
		}
		return sites;
	}

	private static boolean deepEquals(List<int[]> a, List<int[]> b) {
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) {
			if (!Arrays.equals(a.get(i), b.get(i))) return false;
		}
		return true;
	}

	/** Worlds and dumps are large; the first seed's are kept to look at. */
	private void removeWorlds(Path run, long seed) throws IOException {
		if (seed == getSeeds().get().get(0)) return;
		try (Stream<Path> dirs = Files.list(run)) {
			for (Path dir : dirs.filter(p -> p.getFileName().toString().startsWith(seed + "-")).toList()) {
				try (Stream<Path> walk = Files.walk(dir)) {
					for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
				}
			}
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
}
