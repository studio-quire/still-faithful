package oldfaithful.gradle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import oldfaithful.gradle.LibraryTestTask.Stage;

/** The stages that make a world of one of everything and carry it: under other numberings, listener orders, without a mod, and to the real server. */
final class LibraryWorldStages {
	private static final String KEEPER = "Keeper";

	private LibraryWorldStages() {
	}

	/** Chunks loaded for the arena hand their entities to the world at the next tick, so they're touched first. */
	static void snapshot(HarnessServer server, String file) throws Exception {
		server.scene("hprobe touch");
		Thread.sleep(500);
		server.scene("hprobe snapshot " + file);
	}

	static List<String> withoutNumbers(Path snapshot) throws IOException {
		return Files.readAllLines(snapshot, StandardCharsets.UTF_8).stream().filter(l -> !l.startsWith("number\t")).toList();
	}

	/** Name to number, from a boot's registry.txt: blocks and items alike. */
	static Map<String, Integer> numbers(Path registry) throws IOException {
		Map<String, Integer> ids = new HashMap<>();
		for (String line : Files.readAllLines(registry)) {
			String[] f = line.split("\t");
			if (f[0].equals("block") || f[0].equals("item")) ids.put(f[1], Integer.parseInt(f[2]));
		}
		return ids;
	}

	private static String owner(String line) {
		for (String field : line.split("[\t{}\\[\\]=, ]")) {
			int colon = field.indexOf(':');
			if (colon > 0 && field.substring(0, colon).matches("[a-z][a-z0-9_-]*")) return field.substring(0, colon);
		}
		return "unknown";
	}

	/** {@code cell n name}, what a cell's line says but its meta: a block entity may fairly change its block's meta while the world runs. */
	private static String cellName(String line) {
		return line.startsWith("cell\t") ? line.substring(0, line.lastIndexOf('\t')) : null;
	}

	/** Lines of {@code a} that {@code b} lacks and the other way, as failures under {@code rule}. */
	static void diff(LibraryTestTask t, String rule, String what, List<String> a, List<String> b) {
		Set<String> left = new HashSet<>(a), right = new HashSet<>(b);
		Set<String> leftCells = a.stream().map(LibraryWorldStages::cellName).collect(Collectors.toSet()), rightCells = b.stream().map(LibraryWorldStages::cellName).collect(Collectors.toSet());
		int shown = 0;
		for (String line : a) {
			if (right.contains(line)) continue;
			if (rightCells.contains(cellName(line))) t.notes.add(owner(line) + ": " + rule + ": " + what + ": only the meta moved: " + line.replace('\t', ' '));
			else if (shown++ < 20) t.fail(owner(line), rule, what + ": lost " + line.replace('\t', ' '));
		}
		for (String line : b) {
			if (!left.contains(line) && !leftCells.contains(cellName(line)) && shown++ < 20) t.fail(owner(line), rule, what + ": gained " + line.replace('\t', ' '));
		}
	}

	/** A run on the arena: its blocks don't tick, so the cells can be compared across runs. */
	private static List<String> arenaFlags(LibraryTestTask t, Path out) {
		List<String> jvm = new ArrayList<>(t.probeFlags(out));
		jvm.add("-Dfaithful.harness.frozen=true");
		return jvm;
	}

	/** The world of one of everything, made once, for this stage and the next. */
	static Path world(LibraryTestTask t) throws Exception {
		Path dir = t.run.resolve("arena");
		if (Files.isRegularFile(dir.resolve("first.txt"))) return dir;
		try (HarnessServer server = new HarnessServer(t, dir)) {
			server.mods(t.serverMods(t.subjects, true));
			boolean up = server.start(arenaFlags(t, dir.resolve("out")));
			if (up) {
				try (RawClient keeper = new RawClient(server.port, KEEPER).core(true)) {
					String refused = keeper.login(30);
					if (refused != null) t.fail("harness", "arena", "the keeper was refused: " + refused);
					keeper.stand(3, 100);
					server.scene("hprobe place");
					server.scene("hprobe give " + KEEPER);
					keeper.stand(30, 100);
					snapshot(server, "first.txt");
					server.scene("hprobe statics statics-first.txt");
				}
				Thread.sleep(500);
				server.save();
			}
			t.scan("server: arena", server.stop(null));
			if (!up) t.fail("harness", "arena", "the arena's server didn't start");
		}
		return dir;
	}

	/** A server on a copy of the arena's world; {@code then} runs while it's up, with the keeper logged in. */
	private interface WhileUp {
		void run(HarnessServer server, RawClient keeper) throws Exception;
	}

	static void withKeeper(LibraryTestTask t, String where, HarnessServer server, List<String> jvm, boolean core, WhileUp then) throws Exception {
		boolean up = server.start(jvm);
		if (up) {
			try (RawClient keeper = new RawClient(server.port, KEEPER)) {
				if (core) keeper.core(true);
				String refused = keeper.login(30);
				if (refused != null) t.fail("harness", "keeper", where + ": refused: " + refused);
				else {
					keeper.stand(3, 100);
					then.run(server, keeper);
				}
			}
			Thread.sleep(300);
			server.save();
		}
		t.scan("server: " + where, server.stop(null));
		if (!up) t.fail("harness", "failed-to-start", where);
	}

	static void copyWorld(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from.resolve("world"))) {
			for (Path p : walk.toList()) {
				Path target = to.resolve("world").resolve(from.resolve("world").relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else if (!p.getFileName().toString().equals("session.lock")) Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/** Stage 4: the world under another numbering; the listeners in other orders; vanilla's tables, by who changed what. */
	static final class Salts implements Stage {
		private final LibraryTestTask t;

		Salts(LibraryTestTask t) {
			this.t = t;
		}

		@Override
		public void run() throws Exception {
			numberSalt();
			orderSalts();
			tables();
		}

		void numberSalt() throws Exception {
			Path arena = world(t);
			Path dir = t.run.resolve(t.stage).resolve("number");
			try (HarnessServer server = new HarnessServer(t, dir)) {
				server.mods(t.serverMods(t.subjects, true));
				copyWorld(arena, dir);
				List<String> jvm = arenaFlags(t, dir.resolve("out"));
				jvm.add("-Dfaithful.test.numberSalt=shifted");
				withKeeper(t, "number salt", server, jvm, true, (s, keeper) -> {
					snapshot(s, "salted.txt");
					s.scene("hprobe statics statics-salted.txt");
				});
			}
			if (!Files.isRegularFile(dir.resolve("salted.txt"))) return;
			diff(t, "number-salt", "reopened under another numbering", withoutNumbers(arena.resolve("first.txt")), withoutNumbers(dir.resolve("salted.txt")));

			Map<String, Integer> first = numbers(arena.resolve("out/registry.txt")), salted = numbers(dir.resolve("out/registry.txt"));
			Map<Integer, String> names = new HashMap<>();
			first.forEach((name, id) -> names.put(id, name));
			Set<String> held = new HashSet<>();
			for (String line : Files.readAllLines(dir.resolve("salted.txt"))) {
				if (!line.startsWith("number\t")) continue;
				String[] f = line.split("\t");
				int value = (int) Long.parseLong(f[3]);
				String name = names.get(value);
				if (name != null && !Integer.valueOf(value).equals(salted.get(name)) && held.add(f[1] + f[2])) {
					t.fail(owner(f[1]), "number-in-nbt", f[1] + " saved " + f[2] + "=" + value + ", which was " + name + "'s number and isn't now: save the name");
				}
			}
			Map<String, String> before = new HashMap<>();
			for (String line : Files.readAllLines(arena.resolve("statics-first.txt"))) {
				String[] f = line.split("\t");
				before.put(f[1], f[2]);
			}
			for (String line : Files.readAllLines(dir.resolve("statics-salted.txt"))) {
				String[] f = line.split("\t");
				int value = Integer.parseInt(f[2]);
				String name = names.get(value);
				if (f[2].equals(before.get(f[1])) && name != null && !Integer.valueOf(value).equals(salted.get(name))) {
					t.fail(f[0], "number-in-static", f[1] + " holds " + value + ", " + name + "'s number under one numbering only: ask the entry for its id");
				}
			}
		}

		/** The sort key {@code Core.orderKey} gives a mod under a salt. */
		private static String orderKey(String salt, String mod) throws Exception {
			StringBuilder key = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-256").digest((salt + mod).getBytes(StandardCharsets.UTF_8))) key.append(b);
			return key.toString();
		}

		/** Salts that between them turn every pair of mods round; a few at most. */
		static List<String> salts(List<String> mods) throws Exception {
			Set<String> left = new HashSet<>();
			for (String a : mods) {
				for (String b : mods) {
					if (a.compareTo(b) < 0) left.add(a + "|" + b);
				}
			}
			List<String> chosen = new ArrayList<>();
			while (!left.isEmpty() && chosen.size() < 4) {
				String best = null;
				Set<String> bestTurned = Set.of();
				for (int i = 0; i < 2000; i++) {
					String salt = "turn" + i;
					Set<String> turned = new HashSet<>();
					for (String pair : left) {
						String[] ab = pair.split("\\|");
						if (orderKey(salt, ab[0]).compareTo(orderKey(salt, ab[1])) > 0) turned.add(pair);
					}
					if (turned.size() > bestTurned.size()) {
						best = salt;
						bestTurned = turned;
					}
				}
				if (best == null) break;
				chosen.add(best);
				left.removeAll(bestTurned);
			}
			return chosen;
		}

		void orderSalts() throws Exception {
			List<String> ids = t.subjects.stream().map(LibraryMod::id).sorted().collect(Collectors.toList());
			if (ids.size() < 2) return;
			List<String> labels = new ArrayList<>(List.of("plain", "again"));
			labels.addAll(salts(ids));
			List<Callable<List<String>>> jobs = new ArrayList<>();
			for (String label : labels) {
				jobs.add(() -> {
					Path dir = t.run.resolve(t.stage).resolve("order-" + label);
					try (HarnessServer server = new HarnessServer(t, dir)) {
						server.mods(t.serverMods(t.subjects, true));
						List<String> jvm = new ArrayList<>(t.probeFlags(dir.resolve("out")));
						if (label.startsWith("turn")) jvm.add("-Dfaithful.test.orderSalt=" + label);
						withKeeper(t, "order " + label, server, jvm, true, (s, keeper) -> s.scene("hprobe outcomes " + KEEPER + " outcomes.txt"));
					}
					return Files.isRegularFile(dir.resolve("outcomes.txt")) ? Files.readAllLines(dir.resolve("outcomes.txt")) : List.of();
				});
			}
			List<List<String>> results = t.parallel(jobs);
			List<String> plain = results.get(0);
			// What differs between two plain runs is the game's own chance, not the order.
			Set<String> steady = new HashSet<>(plain);
			steady.retainAll(new HashSet<>(results.get(1)));
			Map<String, String> byAct = new LinkedHashMap<>();
			for (String line : plain) {
				if (steady.contains(line)) byAct.put(act(line), line);
			}
			// Whoever shares a hook is who to look at.
			Set<String> sharing = new java.util.TreeSet<>();
			List<String> hooks = new ArrayList<>();
			Path listeners = t.run.resolve(t.stage).resolve("order-plain/out/listeners.txt");
			if (Files.isRegularFile(listeners)) {
				for (String line : Files.readAllLines(listeners)) {
					String[] f = line.split("\t");
					List<String> mods = new ArrayList<>(List.of(f[1].split(",")));
					mods.remove("harness-probe");
					mods.remove("faithful-core");
					if (mods.size() > 1) {
						sharing.addAll(mods);
						hooks.add(f[0] + " " + mods);
					}
				}
			}
			Set<String> reported = new HashSet<>();
			String example = null;
			for (int i = 2; i < results.size(); i++) {
				for (String line : results.get(i)) {
					String mine = byAct.get(act(line));
					if (mine != null && !mine.equals(line) && reported.add(act(line)) && example == null) {
						example = "with the listeners in another order (" + labels.get(i) + "), " + mine.replace('\t', ' ') + " became " + line.replace('\t', ' ');
					}
				}
			}
			if (example != null) {
				t.fail(sharing.isEmpty() ? owner(example) : String.join(", ", sharing), "order-matters", reported.size() + " acts came out differently; " + example + "; hooks shared by mods: " + hooks);
			}
			t.getLogger().lifecycle("order salts {}: {} acts steady of {}", labels.subList(2, labels.size()), byAct.size(), plain.size());
		}

		private static String act(String line) {
			String[] f = line.split("\t");
			return f[0].equals("break") ? f[0] + f[1] + f[2] : f[0] + f[1];
		}

		/** Who changed what: each mod alone against the core alone, then everything together against the sum of those. */
		void tables() throws Exception {
			LibraryStages.ensureBoots(t);
			Path core = t.boots.get("core");
			Path together = t.boots.get("together");
			if (core == null || together == null || !Files.isRegularFile(core.resolve("tables.txt")) || !Files.isRegularFile(together.resolve("tables.txt"))) return;
			Map<String, String> base = LibraryStages.readTable(core.resolve("tables.txt")), all = LibraryStages.readTable(together.resolve("tables.txt"));
			Map<String, Map<String, String>> changes = new TreeMap<>();
			List<String> report = new ArrayList<>();
			for (LibraryMod m : t.subjects) {
				Path alone = t.boots.get("alone-" + m.id());
				if (alone == null || !Files.isRegularFile(alone.resolve("tables.txt"))) continue;
				Map<String, String> table = LibraryStages.readTable(alone.resolve("tables.txt"));
				Set<String> keys = new HashSet<>(table.keySet());
				keys.addAll(base.keySet());
				for (String key : new java.util.TreeSet<>(keys)) {
					if (java.util.Objects.equals(table.get(key), base.get(key))) continue;
					changes.computeIfAbsent(key, k -> new TreeMap<>()).put(m.id(), String.valueOf(table.get(key)));
					report.add(m.id() + ": " + key + ": " + base.get(key) + " -> " + table.get(key));
				}
			}
			for (Map.Entry<String, Map<String, String>> e : changes.entrySet()) {
				String key = e.getKey();
				Map<String, String> who = e.getValue();
				// A mod alone has what it depends on with it: an entry it builds on, or just inherits, is one change and not two.
				for (LibraryMod m : t.subjects) {
					if (who.containsKey(m.id())) LibraryStages.HalfSets.closure(List.of(m), t.subjects).stream().filter(d -> d != m).forEach(d -> who.remove(d.id()));
				}
				if (new HashSet<>(who.values()).size() > 1) {
					t.fail(String.join(", ", who.keySet()), "two-mods-one-table-entry", key + ": " + who + "; together it's " + all.get(key));
				} else if (!who.values().iterator().next().equals(String.valueOf(all.get(key)))) {
					t.fail(String.join(", ", who.keySet()), "changes-together", key + " is " + who + " alone, " + all.get(key) + " together");
				}
			}
			Set<String> keys = new HashSet<>(all.keySet());
			keys.addAll(base.keySet());
			for (String key : keys) {
				if (!changes.containsKey(key) && !java.util.Objects.equals(all.get(key), base.get(key))) {
					t.fail("unknown", "changes-together", key + " is " + base.get(key) + " for each mod alone, " + all.get(key) + " together");
				}
			}
			Files.write(t.run.resolve("tables-changed.txt"), report);
			t.getLogger().lifecycle("{} changes to vanilla's tables, listed in tables-changed.txt", report.size());
		}
	}

	/** Stage 5: saved and reloaded equal; each mod removed and returned; the screen on a real client; the real, unmodified server. */
	static final class RoundTrips implements Stage {
		private final LibraryTestTask t;

		RoundTrips(LibraryTestTask t) {
			this.t = t;
		}

		@Override
		public void run() throws Exception {
			Path arena = world(t);
			if (!Files.isRegularFile(arena.resolve("first.txt"))) return;
			reload(arena);
			List<LibraryMod> content = t.subjects.stream().filter(LibraryMod::content).toList();
			if (content.isEmpty()) return;
			List<Callable<Void>> jobs = new ArrayList<>();
			for (LibraryMod m : content) {
				jobs.add(() -> {
					remove(arena, "without-" + m.id(), List.of(m));
					return null;
				});
			}
			if (content.size() > 1) {
				jobs.add(() -> {
					remove(arena, "no-content", content);
					return null;
				});
			}
			t.parallel(jobs);
			screen(arena, content.get(0));
			realServer(arena);
		}

		private void reload(Path arena) throws Exception {
			Path dir = t.run.resolve(t.stage).resolve("reload");
			try (HarnessServer server = new HarnessServer(t, dir)) {
				server.mods(t.serverMods(t.subjects, true));
				copyWorld(arena, dir);
				withKeeper(t, "reloaded", server, arenaFlags(t, dir.resolve("out")), true, (s, k) -> snapshot(s, "once.txt"));
				withKeeper(t, "reloaded again", server, arenaFlags(t, dir.resolve("out")), true, (s, k) -> snapshot(s, "twice.txt"));
			}
			List<String> first = withoutNumbers(arena.resolve("first.txt"));
			if (Files.isRegularFile(dir.resolve("once.txt"))) diff(t, "round-trip", "saved and reloaded", first, withoutNumbers(dir.resolve("once.txt")));
			if (Files.isRegularFile(dir.resolve("twice.txt"))) diff(t, "round-trip", "saved and reloaded twice", first, withoutNumbers(dir.resolve("twice.txt")));
		}

		/** Each block of the gone mods as its stand-in, by the first run's registry. */
		private static List<String> asStandIns(Path arena, List<String> first, Set<String> gone) throws IOException {
			Map<String, String[]> standIns = new HashMap<>();
			for (String line : Files.readAllLines(arena.resolve("out/registry.txt"))) {
				String[] f = line.split("\t");
				if (f[0].equals("block")) standIns.put(f[1], f);
			}
			List<String> cells = new ArrayList<>();
			for (String line : first) {
				if (!line.startsWith("cell\t")) continue;
				String[] f = line.split("\t");
				String[] standIn = standIns.get(f[2]);
				if (standIn == null || !gone.contains(f[2].substring(0, f[2].indexOf(':')))) cells.add(line);
				else if (!standIn[3].equals("0")) cells.add("cell\t" + f[1] + "\t" + standIn[3] + "\t" + standIn[4]);
			}
			return cells;
		}

		private static List<String> cells(List<String> lines) {
			return lines.stream().filter(l -> l.startsWith("cell\t")).toList();
		}

		private void remove(Path arena, String label, List<LibraryMod> gone) throws Exception {
			Set<String> ids = gone.stream().map(LibraryMod::id).collect(Collectors.toSet());
			// What needs a removed mod goes with it.
			List<LibraryMod> left = LibraryStages.HalfSets.without(t.subjects, ids);
			Set<String> leftIds = left.stream().map(LibraryMod::id).collect(Collectors.toSet());
			Set<String> allGone = t.subjects.stream().map(LibraryMod::id).filter(id -> !leftIds.contains(id)).collect(Collectors.toSet());
			Path dir = t.run.resolve(t.stage).resolve(label);
			boolean anyContent = left.stream().anyMatch(LibraryMod::content);
			try (HarnessServer server = new HarnessServer(t, dir)) {
				server.mods(t.serverMods(left, true));
				copyWorld(arena, dir);
				String line = server.startRefused(arenaFlags(t, dir.resolve("out")), l -> l.contains("accept-missing-content"));
				if (line == null || !line.contains("accept-missing-content")) t.fail(String.join(", ", ids), "removal-refused", "the server didn't stop and say how to go on: " + line);
				server.stop(null);
				Path file = dir.resolve("world/faithful-world.properties");
				Files.writeString(file, Files.readString(file) + "accept-missing-content=true\n");
				withKeeper(t, label, server, arenaFlags(t, dir.resolve("out")), anyContent, (s, k) -> snapshot(s, "removed.txt"));
				server.mods(t.serverMods(t.subjects, true));
				withKeeper(t, label + ", returned", server, arenaFlags(t, dir.resolve("out")), true, (s, k) -> snapshot(s, "returned.txt"));
			}
			List<String> first = withoutNumbers(arena.resolve("first.txt"));
			if (Files.isRegularFile(dir.resolve("removed.txt"))) {
				diff(t, "removal-stand-ins", label + ", as stand-ins", asStandIns(arena, first, allGone), cells(withoutNumbers(dir.resolve("removed.txt"))));
			}
			if (Files.isRegularFile(dir.resolve("returned.txt"))) {
				diff(t, "removal-returns", label + ", blocks back with the mod", cells(first), cells(withoutNumbers(dir.resolve("returned.txt"))));
			}
		}

		/** The real client opening the world without the mod: the screen, drawn, its buttons, the backup, and the world after. */
		private void screen(Path arena, LibraryMod gone) throws Exception {
			Path game = t.run.resolve(t.stage).resolve("missing-screen");
			Path saves = game.resolve("saves").resolve("oldfaithful-startup");
			Files.createDirectories(saves);
			Path staged = game.resolve("staged");
			copyWorld(arena, staged);
			try (Stream<Path> walk = Files.walk(staged.resolve("world"))) {
				for (Path p : walk.sorted(Comparator.naturalOrder()).toList()) {
					Path target = saves.resolve(staged.resolve("world").relativize(p).toString());
					if (Files.isDirectory(p)) Files.createDirectories(target);
					else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			List<LibraryMod> left = LibraryStages.HalfSets.without(t.subjects, Set.of(gone.id()));
			List<String> jvm = new ArrayList<>(t.probeFlags(game.resolve("out")));
			jvm.addAll(List.of("-Doldfaithful.selftest.worldChecksOnly=true", "-Doldfaithful.selftest.recordText=true", "-Doldfaithful.selftest.expectMissing=" + gone.id()));
			List<String> lines = t.client("client: world without " + gone.id(), game, t.clientMods(left, true), jvm);
			boolean backup;
			try (Stream<Path> files = Files.list(game.resolve("saves"))) {
				backup = files.anyMatch(p -> p.getFileName().toString().contains("-backup-") && p.getFileName().toString().endsWith(".zip"));
			}
			if (!backup) t.fail(gone.id(), "removal-screen", "Make a Backup and Play made no backup");
			if (lines.stream().noneMatch(l -> l.contains("PASS missing content screen"))) t.fail(gone.id(), "removal-screen", "the warning screen wasn't seen on a real client");
		}

		/** b1.7.3's own server: it must load, run and save the world, with stand-ins where mods' blocks were. */
		private void realServer(Path arena) throws Exception {
			Path dir = t.run.resolve(t.stage).resolve("real-server");
			Files.createDirectories(dir);
			copyWorld(arena, dir);
			Files.copy(t.getServerJar().get().getAsFile().toPath(), dir.resolve("minecraft_server.jar"));
			int port;
			try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
				port = socket.getLocalPort();
			}
			Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=3", "allow-nether=false", ""));
			try (GameProcess vanilla = new GameProcess(List.of(t.getJavaExecutable().get(), "-Xmx512m", "-jar", "minecraft_server.jar", "nogui"), dir, dir.resolve("harness-output.log"), Map.of())) {
				if (vanilla.await(l -> l.contains("Done ("), 120) == null) {
					t.fail("harness", "real-server", "the real server didn't open the world");
					return;
				}
				try (RawClient plain = new RawClient(port, "Plain")) {
					String refused = plain.login(30);
					if (refused != null) t.fail("harness", "real-server", "a vanilla client was refused: " + refused);
					else plain.stand(10, 100);
				}
				int from = vanilla.lineCount();
				vanilla.send("save-all");
				if (vanilla.awaitFrom(from, l -> l.contains("Save complete."), 60) == null) t.fail("harness", "real-server", "the real server didn't save");
				vanilla.send("stop");
				if (vanilla.waitFor(60) == null) t.fail("harness", "real-server", "the real server didn't stop");
				for (String l : vanilla.lines()) {
					if (l.contains("SEVERE")) t.fail("harness", "real-server", l);
				}
			}
		}
	}
}
