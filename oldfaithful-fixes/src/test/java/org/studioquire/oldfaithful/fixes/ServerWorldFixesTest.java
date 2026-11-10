package org.studioquire.oldfaithful.fixes;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The world fixes from the piston hunt and the read after it (#55, #56, #59, #64, #66, #67, #76, #77, and #74 and #75 on a mob) on the real b1.7.3
 * dedicated server: the scenario mod's scenes (WorldFixes) once with oldfaithful-fixes, then once without it, where each
 * must show beta's bug (so each check is known to see what it checks). Each in a new world of its own.
 * Run with {@code ./gw :oldfaithful-fixes:serverTicksTest} (with #42's test).
 */
@Tag("server")
class ServerWorldFixesTest {
	private final Path base = Path.of(System.getProperty("oldfaithful.run"));
	private final Path run = base.resolve("world-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis());

	@Test
	void worldFixes() throws Exception {
		fresh();
		List<String> fixed = scenes(true);
		List<String> beta = scenes(false);

		assertTrue(line(fixed, "#55").contains("the block below is 54 "), "#55 with the fix: " + line(fixed, "#55"));
		assertTrue(line(beta, "#55").contains("the block below is 0 "), "#55 control: " + line(beta, "#55"));

		assertTrue(line(fixed, "#56").contains(": 0 of 20 drops hurt"), "#56 with the fix: " + line(fixed, "#56"));
		assertTrue(!line(beta, "#56").contains(": 0 of 20 drops hurt"), "#56 control: " + line(beta, "#56"));

		float[] settled = pitches(line(fixed, "#59"));
		for (int i = 1; i < settled.length; i++) assertEquals(-45.0F, settled[i], 0.5F, "#59 with the fix: " + line(fixed, "#59"));
		float[] nodding = pitches(line(beta, "#59"));
		assertTrue(Math.abs(nodding[4] - nodding[5]) > 10.0F, "#59 control: " + line(beta, "#59"));

		assertTrue(line(fixed, "#64").endsWith("east of 0,0, water west: spawns; west of 0,0, water west: spawns; "
				+ "south of 0,0, water north: spawns; north of 0,0, water north: spawns"), "#64 with the fix: " + line(fixed, "#64"));
		assertTrue(line(beta, "#64").endsWith("east of 0,0, water west: spawns; west of 0,0, water west: no; "
				+ "south of 0,0, water north: spawns; north of 0,0, water north: no"), "#64 control: " + line(beta, "#64"));

		assertTrue(line(fixed, "#66").endsWith("in lava no, in water yes"), "#66 with the fix: " + line(fixed, "#66"));
		assertTrue(line(beta, "#66").endsWith("in lava yes, in water yes"), "#66 control: " + line(beta, "#66"));

		assertTrue(line(fixed, "#67").contains(": 2 sand left of 2"), "#67 with the fix: " + line(fixed, "#67"));
		assertTrue(line(beta, "#67").contains(": 1 sand left of 2"), "#67 control: " + line(beta, "#67"));

		assertTrue(line(fixed, "#76").endsWith(": 0 lit"), "#76 with the fix: " + line(fixed, "#76"));
		assertTrue(!line(beta, "#76").endsWith(": 0 lit"), "#76 control: " + line(beta, "#76"));

		assertTrue(line(fixed, "#77").endsWith(": 0 of 96 heights in the water dry; middle over the surface dry, in the air dry"), "#77 with the fix: " + line(fixed, "#77"));
		assertTrue(!line(beta, "#77").contains(": 0 of 96 "), "#77 control: " + line(beta, "#77"));

		String roofed = "#74 zombie across a trench under a roof:", open = "#74 zombie across a trench in the open:";
		assertTrue(line(fixed, roofed).contains(": 0 of 400 sank"), "#74 with the fix: " + line(fixed, roofed));
		assertTrue(!line(beta, roofed).contains(": 0 of 400 sank"), "#74 control: " + line(beta, roofed));
		assertTrue(line(fixed, open).contains(": 0 of 400 sank"), "#74 in the open: " + line(fixed, open));
		assertEquals(line(beta, open), line(fixed, open), "#74 changes nothing in the open");

		assertTrue(line(fixed, "#75").contains(": 0 of 1000 inside"), "#75 with the fix: " + line(fixed, "#75"));
		assertTrue(!line(beta, "#75").contains(": 0 of 1000 inside"), "#75 control: " + line(beta, "#75"));
		try {
			delete(run);
		} catch (IOException e) {
			// A file the stopped server still held for a moment; ServerTicksTest clears old run folders.
			System.out.println("  left " + run + ": " + e);
		}
	}

	private static String line(List<String> lines, String fix) {
		String mark = "[fixes-scenario] " + fix + " ";
		for (String l : lines) {
			int at = l.indexOf(mark);
			if (at >= 0) return l.substring(at + "[fixes-scenario] ".length());
		}
		throw new AssertionError("no line for " + fix);
	}

	private static float[] pitches(String line) {
		String[] p = line.substring(line.indexOf(": ") + 2).trim().split(" ");
		float[] out = new float[p.length];
		for (int i = 0; i < p.length; i++) out[i] = Float.parseFloat(p[i]);
		return out;
	}

	/** One server, a new world, the scenes; its output. */
	private List<String> scenes(boolean withFixes) throws Exception {
		Path mods = run.resolve("mods");
		try (Stream<Path> old = Files.list(mods)) {
			for (Path p : old.toList()) Files.delete(p);
		}
		List<String> wanted = new ArrayList<>(List.of("oldfaithful.fixes.scenario.mod"));
		if (withFixes) wanted.add("oldfaithful.fixes.mod");
		for (String property : wanted) {
			Path mod = Path.of(System.getProperty(property));
			Files.copy(mod, mods.resolve(mod.getFileName()));
		}
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Files.writeString(run.resolve("server.properties"), String.join("\n", "level-name=world-" + (withFixes ? "fixed" : "beta"),
				"online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=4", "spawn-monsters=false",
				"spawn-animals=true", "level-seed=" + System.getProperty("oldfaithful.fixes.seed", ""), ""));
		String java = ProcessHandle.current().info().command().orElse("java");
		Process process = new ProcessBuilder(java, "-Xmx768m", "-Doldfaithful.fixes.scenario=world", "-cp", System.getProperty("oldfaithful.server.classpath"),
				"net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui").directory(run.toFile()).redirectErrorStream(true).start();
		List<String> lines = new ArrayList<>();
		try {
			OutputStream stdin = process.getOutputStream();
			boolean done = false;
			long deadline = System.currentTimeMillis() + 300_000;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = in.readLine()) != null) {
					lines.add(l);
					if (l.contains("[fixes-scenario]") || l.contains("Exception")) System.out.println("  server" + (withFixes ? " (fixes): " : " (beta): ") + l);
					if (l.contains("[fixes-scenario] done") && !done) {
						done = true;
						stdin.write("stop\n".getBytes(StandardCharsets.UTF_8));
						stdin.flush();
					}
					if (System.currentTimeMillis() > deadline) break;
				}
			}
			assertTrue(done, "the scenes never finished:\n" + String.join("\n", lines));
			assertTrue(process.waitFor(120, TimeUnit.SECONDS), "server didn't stop");
		} finally {
			if (process.isAlive()) process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
		}
		for (String l : lines) {
			if (l.contains("[WARNING] Failed to load")) continue; // a new server's missing ban and op lists
			assertTrue(!l.contains("Exception") && !l.contains("[ERROR]") && !l.startsWith("\tat "), "server error: " + l);
		}
		return lines;
	}

	private void fresh() throws IOException {
		if (!base.toString().replace('\\', '/').contains("/local/run/")) throw new AssertionError("unexpected run dir " + base);
		Files.createDirectories(run.resolve("mods"));
	}

	private static void delete(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		}
	}
}
