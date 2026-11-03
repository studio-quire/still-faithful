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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * #42 on the real b1.7.3 dedicated server: a repeater clock built and left running, the server saved and stopped, then
 * started again. With the fix the clock is still running after the restart; then the same world on a server without the
 * fix (the negative control, and proof that vanilla still loads a world with saved ticks): stopped, and no errors.
 * Run with {@code ./gw :oldfaithful-fixes:serverTicksTest}.
 */
@Tag("server")
class ServerTicksTest {
	private static final Pattern SWITCHES = Pattern.compile("\\[fixes-scenario] switches in \\d+ ticks: (\\d+)");
	private static final Pattern CLOCK = Pattern.compile(": (9[34] ){3}9[34]$");

	private final Path base = Path.of(System.getProperty("oldfaithful.run"));
	private final Path run = base.resolve("run-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis());

	@Test
	void repeaterClockSurvivesRestart() throws Exception {
		fresh();
		int built;
		try (Server s = new Server("build", true)) {
			built = switches(s);
			s.saveAndStop();
		}
		assertTrue(built > 0, "the clock never ran (" + built + " switches): the scenario is broken");

		int fixed;
		try (Server s = new Server("watch", true)) {
			fixed = switches(s);
			s.saveAndStop();
			assertClean(s);
		}
		assertTrue(fixed > 0, "with the fix, the clock stood still after a restart");

		int vanilla;
		try (Server s = new Server("watch", false)) {
			vanilla = switches(s);
			s.saveAndStop();
			assertClean(s);
		}
		assertEquals(0, vanilla, "without the fix the clock should stop on a restart (is the scenario testing anything?)");
		System.out.println("  switches: built " + built + ", after a restart " + fixed + ", after a restart without the fix " + vanilla);
	}

	/**
	 * The clock's switches, once its four repeaters (93 off, 94 on) are seen in place: a clock that reads as air or
	 * anything else (its chunk not loaded, or lost) isn't a clock that stood still, and would pass the vanilla control.
	 */
	private static int switches(Server s) throws InterruptedException {
		s.await(l -> SWITCHES.matcher(l).find(), 180);
		String clock = s.lines().stream().filter(l -> l.contains("[fixes-scenario] watching the clock")).findFirst()
				.orElseThrow(() -> new AssertionError("the scenario never watched the clock"));
		assertTrue(CLOCK.matcher(clock).find(), "the clock isn't there: " + clock);
		for (String l : s.lines()) {
			Matcher m = SWITCHES.matcher(l);
			if (m.find()) return Integer.parseInt(m.group(1));
		}
		throw new AssertionError("no count");
	}

	private static void assertClean(Server s) {
		for (String l : s.lines()) {
			assertTrue(!l.contains("Exception") && !l.contains("[ERROR]") && !l.startsWith("\tat "), "server error: " + l);
		}
	}

	// --- the server --------------------------------------------------------------------------------------------------

	private void fresh() throws IOException {
		if (!base.toString().replace('\\', '/').contains("/local/run/")) throw new AssertionError("unexpected run dir " + base);
		Files.createDirectories(base);
		long cutoff = System.currentTimeMillis() - 2 * 3600_000L;
		try (Stream<Path> old = Files.list(base)) {
			for (Path p : old.toList()) {
				if (Files.isDirectory(p) && Files.getLastModifiedTime(p).toMillis() < cutoff) delete(p);
			}
		}
		Files.createDirectories(run.resolve("mods"));
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Files.writeString(run.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false",
				"server-ip=127.0.0.1", "server-port=" + port, "view-distance=4", "spawn-monsters=false", ""));
	}

	private static void delete(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		}
	}

	private final class Server implements AutoCloseable {
		private final Process process;
		private final List<String> lines = new ArrayList<>();
		private final OutputStream stdin;

		Server(String scenario, boolean withFixes) throws IOException {
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
			String java = ProcessHandle.current().info().command().orElse("java");
			process = new ProcessBuilder(java, "-Xmx768m", "-Doldfaithful.fixes.scenario=" + scenario, "-cp", System.getProperty("oldfaithful.server.classpath"),
					"net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui").directory(run.toFile()).redirectErrorStream(true).start();
			stdin = process.getOutputStream();
			Thread reader = new Thread(() -> {
				try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = in.readLine()) != null) {
						synchronized (lines) {
							lines.add(line);
							lines.notifyAll();
						}
						if (line.contains("[fixes-scenario]") || line.contains("Exception")) System.out.println("  server: " + line);
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
