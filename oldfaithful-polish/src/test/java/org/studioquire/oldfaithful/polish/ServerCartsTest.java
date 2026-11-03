package org.studioquire.oldfaithful.polish;

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
 * #31 on the real b1.7.3 dedicated server: the scenario mod's cart scenes (CartScenes, on an east-west and a
 * north-south axis, well away from x = 0) and long boosters, once with the polish mod and once without it, each in a
 * new world. Every scene must come out as its setup predicts (the scenario prints "ok" or "WRONG"); without the mod,
 * on north-south track carts are walls to each other and on east-west track they meet, parallel tracks included.
 * The boosters (two overlapping carts rolling together, empty and carrying pigs): wherever carts meet, the speed they
 * store overflows after about a thousand ticks and they stop, with every position finite and the pigs still riding;
 * without the mod, the north-south ones never boost. Run with
 * {@code ./gw :oldfaithful-polish:serverCartsTest}.
 */
@Tag("server")
class ServerCartsTest {
	private static final int RUN = Integer.getInteger("oldfaithful.polish.scenario.run", 2200);
	private final Path base = Path.of(System.getProperty("oldfaithful.run"));
	private final Path run = base.resolve("carts-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis());

	@Test
	void carts() throws Exception {
		fresh();
		List<String> polished = scenes(true);
		List<String> beta = scenes(false);
		for (List<String> lines : List.of(polished, beta)) {
			assertEquals(12, count(lines, "[polish-scenario] ok: "), "every scene ok:\n" + String.join("\n", said(lines)));
			assertEquals(0, count(lines, "[polish-scenario] WRONG: "), "no scene wrong:\n" + String.join("\n", said(lines)));
		}
		// Boosters: wherever carts meet, a pair rolling together gains speed until the speed it stores overflows, and
		// then stops; beta's guards keep every position finite and the pigs riding. Without the mod, north-south pairs
		// never boost at all.
		for (boolean withMod : new boolean[] {true, false}) {
			for (String booster : new String[] {"east-west empty", "east-west with pigs", "north-south empty", "north-south with pigs"}) {
				String last = last(withMod ? polished : beta, "booster " + booster + " at tick " + RUN + ":");
				String what = (withMod ? "polished" : "beta") + " booster " + booster + ": " + last;
				assertTrue(last.contains("NaN place never") && last.contains("alive"), what);
				if (booster.endsWith("pigs")) assertTrue(last.endsWith(" riding") && !last.endsWith("not riding"), what);
				boolean boosts = withMod || booster.startsWith("east-west");
				assertTrue(boosts ? last.contains("Infinity at") : last.contains("1e99 first never, Infinity never, NaN never"), what);
			}
		}
		try {
			delete(run);
		} catch (IOException e) {
			System.out.println("  left " + run + ": " + e);
		}
	}

	private static long count(List<String> lines, String mark) {
		return lines.stream().filter(l -> l.contains(mark)).count();
	}

	private static List<String> said(List<String> lines) {
		return lines.stream().filter(l -> l.contains("[polish-scenario]")).toList();
	}

	private static String last(List<String> lines, String mark) {
		String found = null;
		for (String l : lines) {
			if (l.contains(mark)) found = l;
		}
		if (found == null) throw new AssertionError("no line for " + mark);
		return found;
	}

	/** One server, a new world, the scenes; its output. */
	private List<String> scenes(boolean polished) throws Exception {
		Path mods = run.resolve("mods");
		try (Stream<Path> old = Files.list(mods)) {
			for (Path p : old.toList()) Files.delete(p);
		}
		List<String> wanted = new ArrayList<>(List.of("oldfaithful.polish.scenario.mod"));
		if (polished) wanted.add("oldfaithful.polish.mod");
		for (String property : wanted) {
			Path mod = Path.of(System.getProperty(property));
			Files.copy(mod, mods.resolve(mod.getFileName()));
		}
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Files.writeString(run.resolve("server.properties"), String.join("\n", "level-name=world-" + (polished ? "polished" : "beta"),
				"online-mode=false", "server-ip=127.0.0.1", "server-port=" + port, "view-distance=4", "spawn-monsters=false",
				"spawn-animals=true", ""));
		String java = ProcessHandle.current().info().command().orElse("java");
		Process process = new ProcessBuilder(java, "-Xmx768m", "-Doldfaithful.polish.scenario=carts", "-Doldfaithful.polish.scenario.polished=" + polished,
				"-Doldfaithful.polish.scenario.debug=" + Boolean.getBoolean("oldfaithful.polish.scenario.debug"), "-Doldfaithful.polish.scenario.run=" + RUN,
				"-cp", System.getProperty("oldfaithful.server.classpath"), "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui")
				.directory(run.toFile()).redirectErrorStream(true).start();
		List<String> lines = new ArrayList<>();
		try {
			OutputStream stdin = process.getOutputStream();
			boolean done = false;
			long deadline = System.currentTimeMillis() + 300_000;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String l;
				while ((l = in.readLine()) != null) {
					lines.add(l);
					if (l.contains("[polish-scenario]") || l.contains("[polish-debug]") || l.contains("Exception")) System.out.println("  server" + (polished ? " (polished): " : " (beta): ") + l);
					if (l.contains("[polish-scenario] done") && !done) {
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
			if (!polished && l.contains("[polish-scenario]")) continue;
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
