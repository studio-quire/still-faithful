package oldfaithful.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** A game process whose combined output is captured line by line, logged to a file, and can be waited on. */
final class GameProcess implements AutoCloseable {
	private final Process process;
	private final List<String> lines = new ArrayList<>();
	private final Writer commands;
	private final Thread reader;

	GameProcess(List<String> command, Path workDir, Path logFile) throws IOException {
		this(command, workDir, logFile, Map.of());
	}

	GameProcess(List<String> command, Path workDir, Path logFile, Map<String, String> environment) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true);
		builder.environment().putAll(environment);
		process = builder.start();
		commands = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
		PrintWriter log = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8), true);
		reader = new Thread(() -> {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = in.readLine()) != null) {
					log.println(line);
					synchronized (lines) {
						lines.add(line);
						lines.notifyAll();
					}
				}
			} catch (IOException ignored) {
				// The process ended.
			} finally {
				log.close();
				synchronized (lines) {
					lines.notifyAll();
				}
			}
		}, "game-output");
		reader.setDaemon(true);
		reader.start();
	}

	/** Waits for a line matching {@code match}; returns it, or null on timeout or if the process ends first. */
	String await(Predicate<String> match, long timeoutSeconds) throws InterruptedException {
		return awaitFrom(0, match, timeoutSeconds);
	}

	/** As {@link #await}, looking only at lines from index {@code from} on (see {@link #lineCount}). */
	String awaitFrom(int from, Predicate<String> match, long timeoutSeconds) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
		int seen = from;
		synchronized (lines) {
			while (true) {
				for (; seen < lines.size(); seen++) {
					if (match.test(lines.get(seen))) return lines.get(seen);
				}
				long left = deadline - System.nanoTime();
				if (left <= 0 || (!process.isAlive() && !reader.isAlive())) return null;
				lines.wait(Math.max(1, TimeUnit.NANOSECONDS.toMillis(Math.min(left, TimeUnit.SECONDS.toNanos(1)))));
			}
		}
	}

	void send(String command) throws IOException {
		commands.write(command + "\n");
		commands.flush();
	}

	/** Exit code, or null if it didn't exit in time. */
	Integer waitFor(long timeoutSeconds) throws InterruptedException {
		if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) return null;
		reader.join(5000);
		return process.exitValue();
	}

	/** An exit code as a person wants it: a Windows status, which is what a native crash leaves, by name. */
	static String describeExit(int exit) {
		if (exit >= 0) return String.valueOf(exit);
		String status = String.format("0x%08X", exit);
		return status + switch (exit) {
			case 0xC0000005 -> " (access violation)";
			case 0xC0000374 -> " (heap corruption, found long after whatever overran)";
			case 0xC0000409 -> " (stack overrun or fail-fast)";
			default -> "";
		};
	}

	/** What the JVM said about crashing in this folder since {@code since}: the file and its summary. */
	static List<String> crashReports(Path dir, long since) throws IOException {
		List<String> reports = new ArrayList<>();
		try (Stream<Path> files = Files.list(dir)) {
			for (Path file : files.filter(p -> p.getFileName().toString().startsWith("hs_err_pid")).toList()) {
				if (Files.getLastModifiedTime(file).toMillis() < since) continue;
				List<String> summary = Files.readAllLines(file, StandardCharsets.ISO_8859_1).stream().filter(l -> l.startsWith("#  ") || l.startsWith("# Problematic")).limit(6).toList();
				reports.add("the JVM crashed, " + file + ": " + String.join(" ", summary).replaceAll("\\s+", " "));
			}
		}
		return reports;
	}

	int lineCount() {
		synchronized (lines) {
			return lines.size();
		}
	}

	List<String> lines() {
		synchronized (lines) {
			return new ArrayList<>(lines);
		}
	}

	@Override
	public void close() {
		// A process started through a script (cmd /c start.bat) leaves its java child running if only it is ended.
		process.descendants().forEach(ProcessHandle::destroyForcibly);
		if (process.isAlive()) {
			process.destroy();
			try {
				if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly();
			} catch (InterruptedException e) {
				process.destroyForcibly();
				Thread.currentThread().interrupt();
			}
		}
	}
}
