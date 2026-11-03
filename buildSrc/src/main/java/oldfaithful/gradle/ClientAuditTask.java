package oldfaithful.gradle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.tasks.TaskAction;

/**
 * Starts the b1.7.3 client through Fabric Loader with our mods, but the self-test mod stops it in its pre-launch
 * entrypoint, before the game's main method runs: no window opens. In between, Mixin force-loads every class our
 * client mixins target, so a broken injection point fails here instead of on the player's screen.
 */
public abstract class ClientAuditTask extends GameTestTask {
	/** A made-up session in a modern launcher's form; it must never be printed or logged. */
	private static final String FAKE_ACCESS = "oldfaithfulFakeAccessToken0123456789";
	private static final String FAKE_SESSION = "token:" + FAKE_ACCESS + ":00000000000000000000000000000000";

	@TaskAction
	public void run() throws Exception {
		Path run = freshRunDir();
		List<String> failures = new ArrayList<>();
		List<String> command = javaCommand("net.fabricmc.loader.impl.launch.knot.KnotClient",
				List.of("-Xmx512m", "-Djava.awt.headless=true", "-Doldfaithful.selftest.client=true"),
				List.of("--gameDir", run.toAbsolutePath().toString(), "--username", "OldFaithfulTest", "--session", FAKE_SESSION));
		List<String> lines;
		try (GameProcess client = new GameProcess(command, run, run.resolve("harness-output.log"))) {
			Integer exit = client.waitFor(180);
			if (exit == null) failures.add("client audit didn't finish within 3 minutes (did the game start?)");
			else if (exit != 0) failures.add("client audit exited with " + exit);
			lines = client.lines();
		}
		checkSelfTest(lines, failures);
		checkLog(run, lines, failures);
		for (String p : problems(lines)) failures.add("log: " + p);
		report("Client audit", lines, failures);
	}

	/** Our log file was written, and neither it nor the console shows the session. */
	private static void checkLog(Path run, List<String> lines, List<String> failures) throws java.io.IOException {
		for (String line : lines) {
			if (line.contains(FAKE_ACCESS)) failures.add("session token printed to the console: " + line);
			// Files at the top of resources/ (the self-test puts Prism's there) are skipped quietly.
			if (line.startsWith("Failed to add ") && !line.substring(14).contains("/")) failures.add("sound loader noise: " + line);
		}
		if (lines.stream().noneMatch(l -> l.contains("session as launched: <redacted>"))) {
			failures.add("console: the session line wasn't printed redacted");
		}
		Path log = run.resolve("logs/purefaithful/latest.log");
		if (!Files.isRegularFile(log)) {
			failures.add("no log file at " + log);
			return;
		}
		String text = Files.readString(log, StandardCharsets.UTF_8);
		if (text.contains(FAKE_ACCESS)) failures.add("session token written to " + log);
		if (!text.contains("session as launched: <redacted>")) failures.add(log + " lacks the self-test's redacted session line");
		if (!text.contains("[oldfaithful-selftest] PASS")) failures.add(log + " lacks the self-test's output");
	}
}
