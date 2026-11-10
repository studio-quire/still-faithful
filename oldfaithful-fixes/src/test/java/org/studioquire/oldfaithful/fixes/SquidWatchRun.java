package org.studioquire.oldfaithful.fixes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * #76 and #77 measured on the real b1.7.3 dedicated server: the scenario mod's SquidWatch with this mod and without it,
 * side by side, each in a new world, each writing its lines to {@code local/run/squid-watch/<label>-<time>/watch.txt}. Takes as
 * long as the ticks at 20 a second; not a pass or fail of the fix. {@code ./gw :oldfaithful-fixes:squidWatch -Psquids.ticks=12000}.
 * Labels ({@code -Psquids.labels}): fixed, beta, and polished (the fixes with Still Faithful's polish mod).
 */
@Tag("squids")
class SquidWatchRun {
	@Test
	void watch() throws Exception {
		ExecutorService pool = Executors.newCachedThreadPool();
		List<Future<?>> runs = new ArrayList<>();
		for (String label : System.getProperty("oldfaithful.squids.labels").split(",")) {
			runs.add(pool.submit(() -> {
				run(label);
				return null;
			}));
		}
		for (Future<?> f : runs) f.get();
		pool.shutdown();
	}

	private static void run(String label) throws Exception {
		Path dir = Path.of(System.getProperty("oldfaithful.run"), label + "-" + System.currentTimeMillis());
		Path mods = dir.resolve("mods");
		Files.createDirectories(mods);
		List<String> wanted = new ArrayList<>(List.of("oldfaithful.fixes.scenario.mod"));
		if (!label.equals("beta")) wanted.add("oldfaithful.fixes.mod");
		// Still Faithful's Squid Surfacing (and the rest of its polish mod) on top of the fixes.
		if (label.equals("polished")) wanted.add("oldfaithful.polish.mod");
		for (String property : wanted) {
			Path mod = Path.of(System.getProperty(property));
			Files.copy(mod, mods.resolve(mod.getFileName()));
		}
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1",
				"server-port=" + port, "view-distance=4", "spawn-monsters=false", "spawn-animals=true", ""));
		String ticks = System.getProperty("oldfaithful.squids.ticks", "12000");
		String java = ProcessHandle.current().info().command().orElse("java");
		List<String> command = new ArrayList<>(List.of(java, "-Xmx768m", "-Doldfaithful.fixes.scenario=squids", "-Doldfaithful.fixes.squidTicks=" + ticks));
		command.addAll(List.of("-cp", System.getProperty("oldfaithful.server.classpath"), "net.fabricmc.loader.impl.launch.knot.KnotServer", "nogui"));
		Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
		boolean done = false;
		try (PrintWriter watch = new PrintWriter(Files.newBufferedWriter(dir.resolve("watch.txt")), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			OutputStream stdin = process.getOutputStream();
			String l;
			while ((l = in.readLine()) != null) {
				if (l.contains("[fixes-scenario]") || l.contains("Exception") || l.contains("[ERROR]") || l.startsWith("\tat ")) {
					watch.println(l);
					System.out.println("  " + label + ": " + l);
				}
				if (l.contains("[fixes-scenario] squids done") && !done) {
					done = true;
					stdin.write("stop\n".getBytes(StandardCharsets.UTF_8));
					stdin.flush();
				}
			}
		} finally {
			if (!process.waitFor(120, TimeUnit.SECONDS)) process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
		}
		assertTrue(done, label + " never finished; see " + dir);
	}
}
