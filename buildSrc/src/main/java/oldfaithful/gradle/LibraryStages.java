package oldfaithful.gradle;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import oldfaithful.gradle.LibraryTestTask.Stage;

/** The harness's stages. Each reads as what it boots, in order, and what it then compares. */
final class LibraryStages {
	private LibraryStages() {
	}

	/** One boot of a server with these mods: starts, a client that belongs there logs in, it saves and stops. */
	static void boot(LibraryTestTask t, String label, List<LibraryMod> set) throws Exception {
		Path dir = t.run.resolve(t.stage).resolve(label);
		Path out = dir.resolve("out");
		try (HarnessServer server = new HarnessServer(t, dir)) {
			server.mods(t.serverMods(set, true));
			boolean up = server.start(t.probeFlags(out));
			if (up) {
				boolean core = set.stream().anyMatch(m -> m.content() || m.match());
				try (RawClient client = new RawClient(server.port, "Boot")) {
					if (core) client.core(true);
					String refused = client.login(30);
					if (refused != null) t.fail(label, "refused", "a client that belongs here was refused: " + refused);
					else client.stand(5, 100);
				}
				if (!server.save()) t.fail(label, "save", "save-all didn't finish");
			}
			Integer[] exit = {null};
			List<String> lines = server.stop(exit);
			t.scan("server: " + label, lines);
			if (!up) {
				String last = lines.isEmpty() ? "no output" : lines.get(lines.size() - 1);
				if (t.failures.stream().noneMatch(f -> f.detail().endsWith("[server: " + label + "]"))) t.fail(label, "failed-to-start", last);
			}
			if (exit[0] == null) t.fail(label, "stop", "the server didn't stop within two minutes");
		}
		synchronized (t.boots) {
			t.boots.put(label, out);
		}
	}

	/** The core alone, each mod alone and everything together, unless a stage already did them. */
	static void ensureBoots(LibraryTestTask t) throws Exception {
		List<Callable<Void>> jobs = new ArrayList<>();
		if (!t.boots.containsKey("core")) jobs.add(() -> {
			boot(t, "core", List.of());
			return null;
		});
		if (!t.boots.containsKey("together")) jobs.add(() -> {
			boot(t, "together", t.subjects);
			return null;
		});
		for (LibraryMod m : t.subjects) {
			if (!t.boots.containsKey("alone-" + m.id())) jobs.add(() -> {
				boot(t, "alone-" + m.id(), HalfSets.closure(List.of(m), t.subjects));
				return null;
			});
		}
		t.parallel(jobs);
	}

	/** Stage 2: everything at once, on one server, then a hidden client for what only it knows. */
	static final class Registry implements Stage {
		private final LibraryTestTask t;

		Registry(LibraryTestTask t) {
			this.t = t;
		}

		@Override
		public void run() throws Exception {
			boot(t, "all", t.subjects);
			if (!t.failures.isEmpty() && t.failures.stream().anyMatch(f -> f.rule().equals("failed-to-start") || f.rule().equals("declared-twice"))) return;
			Path game = t.run.resolve(t.stage).resolve("client");
			Files.createDirectories(game);
			List<String> jvm = new ArrayList<>(t.probeFlags(game.resolve("out")));
			jvm.add("-Doldfaithful.selftest.worldChecksOnly=true");
			t.client("client: all", game, t.clientMods(t.subjects, true), jvm);
		}
	}

	/** Stage 3: each mod alone, everything, seeded half-sets, hidden clients on a sample, then the editions. */
	static final class Combinations implements Stage {
		private final LibraryTestTask t;

		Combinations(LibraryTestTask t) {
			this.t = t;
		}

		@Override
		public void run() throws Exception {
			List<List<LibraryMod>> halves = HalfSets.plan(t.subjects, t.getSeed().get());
			t.getLogger().lifecycle("{} half-sets for {} mods", halves.size(), t.subjects.size());
			List<Callable<Void>> jobs = new ArrayList<>();
			jobs.add(() -> {
				boot(t, "core", List.of());
				return null;
			});
			for (LibraryMod m : t.subjects) {
				jobs.add(() -> {
					boot(t, "alone-" + m.id(), withDepends(List.of(m)));
					return null;
				});
			}
			jobs.add(() -> {
				boot(t, "together", t.subjects);
				return null;
			});
			int n = 0;
			for (List<LibraryMod> half : halves) {
				String label = "half-" + ++n;
				jobs.add(() -> {
					boot(t, label, half);
					return null;
				});
			}
			t.parallel(jobs);

			List<Callable<Void>> clients = new ArrayList<>();
			clients.add(clientJob("client-together", t.subjects));
			for (int i = 0; i < Math.min(t.getClientSamples().get(), halves.size()); i++) clients.add(clientJob("client-half-" + (i + 1), halves.get(i)));
			t.parallel(clients);
			editions();
		}

		private List<LibraryMod> withDepends(List<LibraryMod> set) {
			return HalfSets.closure(set, t.subjects);
		}

		private Callable<Void> clientJob(String label, List<LibraryMod> set) {
			return () -> {
				Path game = t.run.resolve(t.stage).resolve(label);
				Files.createDirectories(game);
				List<String> jvm = new ArrayList<>(t.probeFlags(game.resolve("out")));
				jvm.add("-Doldfaithful.selftest.worldChecksOnly=true");
				t.client("client: " + label, game, t.clientMods(set, true), jvm);
				return null;
			};
		}

		private record Edition(String name, List<Path> client, List<Path> server, List<String> jvm) {
		}

		private List<Edition> editionList() {
			List<Path> old = new ArrayList<>(), still = new ArrayList<>();
			t.getOldMods().getFiles().forEach(f -> old.add(f.toPath()));
			t.getStillMods().getFiles().forEach(f -> still.add(f.toPath()));
			List<Edition> list = new ArrayList<>();
			list.add(new Edition("pure", t.clientMods(t.subjects, true), t.serverMods(t.subjects, true), List.of()));
			if (!old.isEmpty()) list.add(new Edition("old", with(t.clientMods(t.subjects, true), old), with(t.serverMods(t.subjects, true), old), List.of()));
			if (!still.isEmpty()) {
				List<Path> oldStill = with(old, still);
				list.add(new Edition("still", with(t.clientMods(t.subjects, true), oldStill), with(t.serverMods(t.subjects, true), oldStill),
						t.getStillEdition().get().isEmpty() ? List.of() : List.of(LibraryTestTask.flag("oldfaithful.selftest.edition", t.getStillEdition().get()))));
			}
			return list;
		}

		private static List<Path> with(List<Path> a, List<Path> b) {
			List<Path> all = new ArrayList<>(a);
			all.addAll(b);
			return all;
		}

		/** Each edition under the set, its switches as they come and every one turned over, then each client on each other edition's server. */
		private void editions() throws Exception {
			List<Edition> editions = editionList();
			List<Callable<Void>> jobs = new ArrayList<>();
			for (Edition e : editions) jobs.add(() -> {
				edition(e);
				return null;
			});
			t.parallel(jobs);

			for (Edition host : editions) {
				Path dir = t.run.resolve(t.stage).resolve("host-" + host.name);
				try (HarnessServer server = new HarnessServer(t, dir)) {
					server.mods(host.server);
					if (server.start(List.of())) {
						List<Callable<Void>> joins = new ArrayList<>();
						for (Edition guest : editions) {
							joins.add(() -> {
								Path game = dir.resolve("guest-" + guest.name);
								Files.createDirectories(game);
								List<String> jvm = new ArrayList<>(List.of("-Doldfaithful.selftest.joinServer=127.0.0.1:" + server.port, "-Doldfaithful.selftest.joinWorldChecks=true"));
								jvm.addAll(guest.jvm);
								t.client("client: " + guest.name + " on a " + host.name + " server", game, guest.client, jvm);
								return null;
							});
						}
						// Two at a time at most: a server's login throttle goes by address, and the clients' names are the same.
						for (Callable<Void> join : joins) join.call();
					}
					t.scan("server: host " + host.name, server.stop(null));
				}
			}
		}

		private void edition(Edition e) throws Exception {
			Path dir = t.run.resolve(t.stage).resolve("edition-" + e.name);
			Path game = dir.resolve("client");
			Files.createDirectories(game);
			List<String> jvm = new ArrayList<>(List.of("-Doldfaithful.selftest.worldChecksOnly=true"));
			jvm.addAll(e.jvm);
			t.client("client: " + e.name, game, e.client, jvm);
			try (HarnessServer server = new HarnessServer(t, dir.resolve("server"))) {
				server.mods(e.server);
				boolean up = server.start(List.of());
				if (up) server.save();
				t.scan("server: " + e.name, server.stop(null));
				if (!up) t.fail(e.name, "failed-to-start", "the server didn't start");
				if (flipSwitches(game.resolve("config")) + flipSwitches(server.dir.resolve("config")) == 0 && !e.name.equals("pure")) t.fail(e.name, "switches", "no switches found to turn over");
				up = server.start(List.of());
				if (up) server.save();
				t.scan("server: " + e.name + ", switches turned over", server.stop(null));
				if (!up) t.fail(e.name, "failed-to-start", "the server didn't start with its switches turned over");
			}
			t.client("client: " + e.name + ", switches turned over", game, e.client, jvm);
		}

		/** Every boolean in the editions' config files, the other way. */
		private static int flipSwitches(Path config) throws IOException {
			if (!Files.isDirectory(config)) return 0;
			int flipped = 0;
			try (Stream<Path> files = Files.list(config)) {
				for (Path file : files.filter(p -> p.getFileName().toString().matches("(oldfaithful|stillfaithful)-.*\\.properties")).toList()) {
					List<String> lines = new ArrayList<>();
					for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
						if (line.endsWith("=true")) {
							line = line.substring(0, line.length() - 4) + "false";
							flipped++;
						} else if (line.endsWith("=false")) {
							line = line.substring(0, line.length() - 5) + "true";
							flipped++;
						}
						lines.add(line);
					}
					Files.write(file, lines, StandardCharsets.ISO_8859_1);
				}
			}
			return flipped;
		}
	}

	/** Seeded half-sets so that every pair of mods meets with some third absent. */
	static final class HalfSets {
		private HalfSets() {
		}

		static List<LibraryMod> closure(List<LibraryMod> set, List<LibraryMod> all) {
			Map<String, LibraryMod> byId = new LinkedHashMap<>();
			all.forEach(m -> byId.put(m.id(), m));
			Map<String, LibraryMod> out = new TreeMap<>();
			List<LibraryMod> todo = new ArrayList<>(set);
			while (!todo.isEmpty()) {
				LibraryMod m = todo.remove(todo.size() - 1);
				if (out.put(m.id(), m) != null) continue;
				for (String d : m.depends()) {
					if (byId.containsKey(d)) todo.add(byId.get(d));
				}
			}
			return new ArrayList<>(out.values());
		}

		/** The set without these mods, and without whatever needs them, however far down. */
		static List<LibraryMod> without(List<LibraryMod> all, java.util.Set<String> gone) {
			java.util.Set<String> out = new java.util.HashSet<>(gone);
			List<LibraryMod> left = new ArrayList<>(all);
			while (left.removeIf(m -> out.contains(m.id()) || (m.depends().stream().anyMatch(out::contains) && out.add(m.id())))) {
				// Each pass finds what needed the last.
			}
			return left;
		}

		static List<List<LibraryMod>> plan(List<LibraryMod> mods, long seed) {
			int n = mods.size();
			List<List<LibraryMod>> sets = new ArrayList<>();
			if (n < 3) return sets;
			BitSet left = new BitSet(n * n);
			for (int a = 0; a < n; a++) {
				for (int b = a + 1; b < n; b++) left.set(a * n + b);
			}
			Random random = new Random(seed);
			for (int misses = 0; !left.isEmpty() && misses < 200; ) {
				List<LibraryMod> best = null;
				int bestCover = 0;
				for (int candidate = 0; candidate < 40; candidate++) {
					List<LibraryMod> shuffled = new ArrayList<>(mods);
					java.util.Collections.shuffle(shuffled, random);
					List<LibraryMod> set = closure(shuffled.subList(0, Math.max(2, (n + 1) / 2)), mods);
					if (set.size() == n) continue;
					int cover = cover(set, mods, left, false);
					if (cover > bestCover) {
						best = set;
						bestCover = cover;
					}
				}
				if (best == null) {
					misses++;
					continue;
				}
				cover(best, mods, left, true);
				sets.add(best);
			}
			return sets;
		}

		private static int cover(List<LibraryMod> set, List<LibraryMod> mods, BitSet left, boolean clear) {
			int n = mods.size(), covered = 0;
			for (int a = 0; a < n; a++) {
				if (!set.contains(mods.get(a))) continue;
				for (int b = a + 1; b < n; b++) {
					if (!set.contains(mods.get(b)) || !left.get(a * n + b)) continue;
					covered++;
					if (clear) left.clear(a * n + b);
				}
			}
			return covered;
		}
	}

	static Map<String, String> readTable(Path file) throws IOException {
		Map<String, String> table = new TreeMap<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			int eq = line.indexOf('=');
			table.put(line.substring(0, eq), line.substring(eq + 1));
		}
		return table;
	}

	static Properties properties(Path file) throws IOException {
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
			p.load(r);
		}
		return p;
	}

	static <T> List<T> list(Collection<T> c) {
		return new ArrayList<>(c);
	}

	static List<String> words(String... w) {
		return Arrays.asList(w);
	}
}
