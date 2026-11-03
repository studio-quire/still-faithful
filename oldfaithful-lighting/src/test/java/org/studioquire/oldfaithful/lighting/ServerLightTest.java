package org.studioquire.oldfaithful.lighting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The real b1.7.3 dedicated server, the same seed and the same scenario (spawn generation, new chunks around old
 * ones, chunks unloaded and loaded again, explosions, light sources, water covered), without and with the lighting
 * mod; then every saved chunk's light against the rule (LightCheck). Also a world made without the mod, opened with
 * it: what the mod heals in an existing world. Run with {@code ./gw :oldfaithful-lighting:serverLightTest}.
 */
@Tag("server")
class ServerLightTest {
	/** Cells the paced check may find wrong: vanilla's own updates (decoration's) still wait in the queue there. */
	private static final int PACED_ALLOWED = 0;
	private List<String> lastLine = List.of();
	private int pacedWrong = -1;

	private final Path base = Path.of(System.getProperty("oldfaithful.run"));
	private final Path run = base.resolve("run-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis());
	private static final java.util.Set<Process> live = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			synchronized (live) {
				for (Process p : live) p.destroyForcibly();
			}
		}));
	}

	@Test
	void lightMatchesTheRule() throws Exception {
		if (!base.toString().replace('\\', '/').contains("/local/run/")) throw new AssertionError("unexpected run dir " + base);
		Files.createDirectories(base);
		clearOld();
		String only = System.getProperty("oldfaithful.lighting.only", "vanilla,lighting,healed,paced,lavaoff");
		List<String> variants = Arrays.asList(only.split(","));
		Path lightingMod = Path.of(System.getProperty("oldfaithful.lighting.mod"));
		Path savesMod = Path.of(System.getProperty("oldfaithful.saves.mod"));
		Path scenarioMod = Path.of(System.getProperty("oldfaithful.scenario.mod"));

		Map<String, Map<String, LightCheck.Result>> results = new LinkedHashMap<>();
		if (variants.contains("vanilla") || variants.contains("healed")) {
			Path dir = run.resolve("vanilla");
			results.put("vanilla", play(dir, List.of(scenarioMod), "play"));
			if (variants.contains("healed")) {
				// The vanilla world opened with the mod, every saved chunk loaded.
				Path healed = run.resolve("healed");
				copy(dir.resolve("world"), healed.resolve("world"));
				results.put("healed", play(healed, List.of(lightingMod, savesMod, scenarioMod), "heal"));
			}
		}
		if (variants.contains("lighting")) {
			// Played with the mod; a server stopping mid-tick can leave a few updates queued (as vanilla's can), which
			// the mod's check finishes when those chunks next load: opened again, every saved chunk loaded.
			Path dir = run.resolve("lighting");
			results.put("lighting", play(dir, List.of(lightingMod, savesMod, scenarioMod), "play"));
			Path again = run.resolve("lighting-reopened");
			copy(dir.resolve("world"), again.resolve("world"));
			results.put("lighting, reopened", play(again, List.of(lightingMod, savesMod, scenarioMod), "heal"));
		}
		if (variants.contains("paced")) {
			// Exploring the Nether at a single player client's pace (one lighting pass of 500 updates a frame, 60 frames
			// a second: 3 passes a tick), checked in memory the moment the last chunk is in, before the queue is done.
			// Light a chunk needs on entering (generated lava above all) mustn't wait behind the queue: it did, and far
			// lava stayed dark until the player came near (2026-11-02).
			Path dir = run.resolve("paced");
			play(dir, List.of(lightingMod, savesMod, scenarioMod), "paced", "-Doldfaithful.lighting.pace=3");
			String line = lastLine.stream().filter(l -> l.contains("paced check:")).findFirst().orElseThrow();
			int wrong = Integer.parseInt(line.replaceAll(".*paced check: (-?[0-9]+) cells.*", "$1"));
			System.out.println("[ServerLightTest] paced, nether, before the queue is done: " + wrong + " cells disagree");
			pacedWrong = wrong;
		}
		Map<String, LightCheck.Result> lavaOffBeta = null;
		if (variants.contains("lavaoff")) {
			// lava.lightGenerated=false: lava from new terrain stays dark as in beta, everything else as with the mod.
			// Checked twice: against the full rule, where only the dark lava and what it would light are wrong (as in
			// vanilla), and against the rule with beta's unlit lava, where nothing is.
			Path dir = run.resolve("lavaoff");
			results.put("lava off", play(dir, List.of(lightingMod, savesMod, scenarioMod), "play", "-Doldfaithful.lighting.lava.lightGenerated=false"));
			lavaOffBeta = LightCheck.checkWorld(dir.resolve("world").toFile(), true);
		}
		for (Map.Entry<String, Map<String, LightCheck.Result>> e : results.entrySet()) {
			for (Map.Entry<String, LightCheck.Result> d : e.getValue().entrySet()) {
				System.out.println("[ServerLightTest] " + e.getKey() + ", " + d.getKey() + ": " + d.getValue());
			}
		}
		for (String v : new String[] {"lighting", "lighting, reopened", "healed"}) {
			Map<String, LightCheck.Result> r = results.get(v);
			if (r == null) continue;
			for (Map.Entry<String, LightCheck.Result> d : r.entrySet()) {
				assertTrue(d.getValue().chunksCompared > 100, v + " " + d.getKey() + ": too few chunks compared");
				assertEquals(0, d.getValue().total(0) + d.getValue().total(1), v + " " + d.getKey() + " light differs from the rule");
			}
		}
		if (lavaOffBeta != null) {
			for (Map.Entry<String, LightCheck.Result> d : lavaOffBeta.entrySet()) {
				System.out.println("[ServerLightTest] lava off, " + d.getKey() + ", against beta's unlit lava: " + d.getValue());
				assertTrue(d.getValue().chunksCompared > 100, "lava off " + d.getKey() + ": too few chunks compared");
				assertEquals(0, d.getValue().total(0) + d.getValue().total(1), "lava off " + d.getKey() + ": light other than beta's dark lava is wrong");
				LightCheck.Result full = results.get("lava off").get(d.getKey());
				assertEquals(0, full.total(0), "lava off " + d.getKey() + ": sky light wrong");
				assertTrue(full.wrong[1][0] > 0, "lava off " + d.getKey() + ": no dark lava at all (the switch did nothing?)");
				assertEquals(0, full.wrong[1][1], "lava off " + d.getKey() + ": block light too bright");
			}
		}
		if (pacedWrong >= 0) assertTrue(pacedWrong <= PACED_ALLOWED, "paced: " + pacedWrong + " cells still wrong when the last chunk entered");
		if (!Boolean.getBoolean("oldfaithful.lighting.keep")) delete(run);
	}

	/** Runs the server in {@code dir} with these mods (added to any already there) and the scenario in this mode. */
	private Map<String, LightCheck.Result> play(Path dir, List<Path> mods, String scenario, String... jvm) throws Exception {
		Files.createDirectories(dir.resolve("mods"));
		for (Path m : mods) Files.copy(m, dir.resolve("mods").resolve(m.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "level-seed=404", "online-mode=false",
				"server-ip=127.0.0.1", "server-port=" + port, "view-distance=4", "allow-nether=true", "spawn-animals=false",
				"spawn-monsters=false", ""));
		long start = System.currentTimeMillis();
		try (Server s = new Server(dir, scenario, jvm)) {
			s.await(l -> l.contains("Done ("), 600);
			s.await(l -> l.contains("[lighting-scenario] done"), 600);
			s.saveAndStop();
			lastLine = s.lines();
			// The paced scenario's own check comes first, before the queue is done; the test judges it.
			boolean pacedCheck = scenario.equals("paced");
			for (String l : s.lines()) {
				String low = l.toLowerCase();
				assertTrue(!(low.contains("mixin") && (low.contains("error") || low.contains("failed"))), "Mixin problem: " + l);
				// (A new server's missing ban and op lists are reported as FileNotFoundExceptions.)
				assertTrue(!l.contains("Exception") || l.contains("FileNotFoundException"), dir.getFileName() + ": " + l);
				// With the mod, every full save checks the loaded world in memory too (Verify).
				if (l.contains("verify:") && l.contains("cells disagree")) {
					if (pacedCheck) pacedCheck = false;
					else assertTrue(l.contains(" 0 cells disagree"), dir.getFileName() + ": " + l);
				}
			}
		}
		System.out.println("[ServerLightTest] " + dir.getFileName() + " played in " + (System.currentTimeMillis() - start) / 1000 + " s");
		return LightCheck.checkWorld(dir.resolve("world").toFile());
	}

	private void clearOld() throws IOException {
		long cutoff = System.currentTimeMillis() - 2 * 3600_000L;
		try (Stream<Path> old = Files.list(base)) {
			for (Path p : old.toList()) {
				if (Files.isDirectory(p) && p.getFileName().toString().startsWith("run-") && Files.getLastModifiedTime(p).toMillis() < cutoff) delete(p);
			}
		}
	}

	private static void copy(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path p : walk.toList()) {
				Path t = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(t);
				else Files.copy(p, t);
			}
		}
		Files.deleteIfExists(to.resolve("session.lock"));
	}

	private static void delete(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		}
	}

	private static final class Server implements AutoCloseable {
		private final Process process;
		private final List<String> lines = new ArrayList<>();
		private final OutputStream stdin;

		Server(Path dir, String scenario, String... jvm) throws IOException {
			String java = ProcessHandle.current().info().command().orElse("java");
			List<String> command = new ArrayList<>(List.of(java));
			command.addAll(Arrays.asList(jvm));
			command.addAll(List.of("-Xmx1g", "-Doldfaithful.lighting.scenario=" + scenario, "-Doldfaithful.lighting.verify=true",
					"-Doldfaithful.lighting.stats=true", "-Doldfaithful.lighting.idle=" + Integer.getInteger("oldfaithful.lighting.idle", 40), "-cp",
					System.getProperty("oldfaithful.server.classpath"), "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui"));
			process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
			live.add(process);
			process.onExit().thenRun(() -> live.remove(process));
			stdin = process.getOutputStream();
			Thread reader = new Thread(() -> {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = in.readLine()) != null) {
						synchronized (lines) {
							lines.add(line);
							lines.notifyAll();
						}
						if (line.contains("lighting") || line.startsWith("	at ") || line.contains("Exception") || line.contains("updates") || line.contains("Done (")) System.out.println("  server: " + line);
					}
				} catch (IOException ignored) {
					// The process went away.
				}
			});
			reader.setDaemon(true);
			reader.start();
		}

		void await(Predicate<String> p, int seconds) throws InterruptedException {
			long deadline = System.currentTimeMillis() + seconds * 1000L;
			synchronized (lines) {
				int seen = 0;
				while (true) {
					for (; seen < lines.size(); seen++) if (p.test(lines.get(seen))) return;
					long left = deadline - System.currentTimeMillis();
					if (left <= 0 || !process.isAlive() && seen >= lines.size()) throw new AssertionError("timed out; output:\n" + String.join("\n", lines));
					lines.wait(Math.min(left, 500));
				}
			}
		}

		void send(String command) throws IOException {
			stdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
			stdin.flush();
		}

		void saveAndStop() throws Exception {
			send("save-all");
			await(l -> l.contains("Save complete."), 120);
			send("stop");
			assertTrue(process.waitFor(120, TimeUnit.SECONDS), "server didn't stop");
		}

		List<String> lines() {
			synchronized (lines) {
				return new ArrayList<>(lines);
			}
		}

		@Override
		public void close() throws InterruptedException {
			if (process.isAlive()) {
				process.destroyForcibly();
				process.waitFor(30, TimeUnit.SECONDS);
			}
		}
	}
}
