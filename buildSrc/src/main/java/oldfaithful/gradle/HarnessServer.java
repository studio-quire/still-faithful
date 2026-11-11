package oldfaithful.gradle;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** A real server in a folder of its own, for the harness: started and stopped as often as a stage needs, on a port it keeps. */
final class HarnessServer implements AutoCloseable {
	private final LibraryTestTask task;
	final Path dir;
	final int port;
	private GameProcess process;
	private int runs;
	private boolean up = true;
	private final long created = System.currentTimeMillis();

	HarnessServer(LibraryTestTask task, Path dir) throws IOException {
		this.task = task;
		this.dir = dir;
		Files.createDirectories(dir);
		try (ServerSocket socket = new ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		Files.writeString(dir.resolve("server.properties"), String.join("\n", "level-name=world", "online-mode=false", "server-ip=127.0.0.1", "server-port=" + port,
				"view-distance=3", "max-players=20", "spawn-monsters=false", "spawn-animals=false", "pvp=true",
				// The Nether's copy of the world data moves the spawn after a first start, and the scenes go by the spawn.
				"allow-nether=false", ""));
		Files.writeString(dir.resolve("ops.txt"), "OldFaithfulTest\nMonkey\n");
	}

	/** Lines of server.properties, in place of what they were. */
	HarnessServer properties(Map<String, String> changes) throws IOException {
		List<String> lines = new ArrayList<>();
		Set<String> left = new LinkedHashSet<>(changes.keySet());
		for (String line : Files.readAllLines(dir.resolve("server.properties"))) {
			String key = line.split("=", 2)[0];
			left.remove(key);
			lines.add(changes.containsKey(key) ? key + "=" + changes.get(key) : line);
		}
		for (String key : left) lines.add(key + "=" + changes.get(key));
		Files.write(dir.resolve("server.properties"), lines);
		return this;
	}

	/** What's in mods/, replacing what was. */
	HarnessServer mods(Collection<Path> jars) throws IOException {
		Path mods = Files.createDirectories(dir.resolve("mods"));
		try (Stream<Path> old = Files.list(mods)) {
			for (Path p : old.toList()) Files.delete(p);
		}
		Set<String> names = new LinkedHashSet<>();
		for (Path jar : jars) {
			if (names.add(jar.getFileName().toString())) Files.copy(jar, mods.resolve(jar.getFileName()));
		}
		return this;
	}

	/** Starts it; false if it stopped before it was ready, which {@link #stop} then reports. */
	boolean start(List<String> jvm) throws Exception {
		launch(jvm);
		return awaitStart();
	}

	/** Starts its process and comes back at once, for a start that something else goes on beside: {@link #awaitStart}. */
	void launch(List<String> jvm) throws IOException {
		process = new GameProcess(task.serverCommand(jvm), dir, dir.resolve("harness-output-" + ++runs + ".log"), Map.of());
		up = false;
	}

	/** Waits until it's ready; false if it stopped before that, which {@link #stop} then reports. */
	boolean awaitStart() throws InterruptedException {
		// A failed start leaves the process up: the console thread outlives the server's.
		String line = process.await(l -> l.contains("Done (") || l.contains("Stopping server") || l.startsWith("java.lang."), 180);
		up = line != null && line.contains("Done (");
		return up;
	}

	/** Starts it, expecting it to refuse: the line that says so, or null. */
	String startRefused(List<String> jvm, java.util.function.Predicate<String> line) throws Exception {
		process = new GameProcess(task.serverCommand(jvm), dir, dir.resolve("harness-output-" + ++runs + ".log"), Map.of());
		return process.await(l -> line.test(l) || l.contains("Done ("), 120);
	}

	/** Runs a probe scene, or another command that ends in the probe's DONE line, and waits for it. */
	boolean scene(String command) throws Exception {
		int from = process.lineCount();
		process.send(command);
		String name = command.split(" ")[1];
		return process.awaitFrom(from, l -> l.contains("[library-harness] DONE " + name), 120) != null;
	}

	void send(String command) throws IOException {
		process.send(command);
	}

	boolean save() throws Exception {
		int from = process.lineCount();
		process.send("save-all");
		return process.awaitFrom(from, l -> l.contains("Save complete."), 60) != null;
	}

	int lineCount() {
		return process.lineCount();
	}

	/** Waits for a line holding {@code text}, from line {@code from} on: true if it came within {@code seconds}. */
	boolean awaitLine(int from, String text, double seconds) throws Exception {
		return process.awaitFrom(from, l -> l.contains(text), (long) Math.ceil(seconds)) != null;
	}

	/** Whether the console still answers within {@code seconds}. */
	boolean answers(int seconds) throws Exception {
		int from = process.lineCount();
		process.send("list");
		return process.awaitFrom(from, l -> l.contains("Connected players"), seconds) != null;
	}

	boolean running() {
		return process != null && process.lineCount() >= 0 && !process.lines().isEmpty();
	}

	/** Output so far of the current run. */
	List<String> lines() {
		return process == null ? List.of() : process.lines();
	}

	/** For a server that stops by itself: its output once it has. */
	List<String> waitStopped(Integer[] exit) throws Exception {
		exit[0] = process.waitFor(120);
		reportCrash(exit[0]);
		List<String> out = new ArrayList<>(process.lines());
		process.close();
		process = null;
		return out;
	}

	/** Stops it and returns the run's whole output. A null {@code exit[0]} means it had to be killed. */
	List<String> stop(Integer[] exit) throws Exception {
		if (process == null) return List.of();
		try {
			process.send("stop");
		} catch (IOException e) {
			// Already gone.
		}
		// Its stack trace is still coming, if it failed.
		if (!up) Thread.sleep(1000);
		Integer code = process.waitFor(up ? 120 : 5);
		if (exit != null) exit[0] = up ? code : Integer.valueOf(-1);
		reportCrash(code);
		List<String> out = new ArrayList<>(process.lines());
		process.close();
		process = null;
		return out;
	}

	/** A server's native death reads as nothing else: it was simply gone. */
	private void reportCrash(Integer code) throws IOException {
		if (code != null && code < 0) task.fail("unknown", "native-crash", "a server in " + dir.getFileName() + " exited with " + GameProcess.describeExit(code));
		for (String report : GameProcess.crashReports(dir, created)) task.fail("unknown", "native-crash", report);
	}

	@Override
	public void close() {
		if (process != null) process.close();
	}
}
