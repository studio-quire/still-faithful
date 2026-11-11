package oldfaithful.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Sees that no game outlives the build that started it. A task ends its games itself when it ends, however it ends,
 * but a Gradle daemon killed outright (another session's {@code gradle --stop}, the task manager) ends nothing, and
 * its servers stayed up, a gigabyte and more each, until someone found them. While any game of this process runs, an
 * {@link OrphanReaper} runs beside it knowing each game by its process id and start, and ends them when this process
 * is gone; when the last game ends, so does it.
 */
final class Orphans {
	private Orphans() {
	}

	private static final Set<Process> running = new HashSet<>();
	private static Process reaper;
	private static Writer toReaper;

	/** Has {@code process}, and whatever it starts, ended if this process dies before it. */
	static synchronized void watch(Process process) {
		running.add(process);
		try {
			if (reaper == null || !reaper.isAlive()) {
				start();
				// A reaper that died took what it knew with it.
				for (Process p : running) toReaper.write(OrphanReaper.line(p.toHandle()));
			} else {
				toReaper.write(OrphanReaper.line(process.toHandle()));
			}
			toReaper.flush();
		} catch (IOException | RuntimeException e) {
			// Unwatched, as every game was before: only a killed build would show the difference.
			if (reaper != null) reaper.destroyForcibly();
			reaper = null;
			toReaper = null;
		}
		process.onExit().thenRun(() -> ended(process));
	}

	private static synchronized void ended(Process process) {
		running.remove(process);
		if (!running.isEmpty() || toReaper == null) return;
		// The end of its input is its cue, and with nothing left running it only goes.
		try {
			toReaper.close();
		} catch (IOException e) {
			// Gone already.
		}
		reaper = null;
		toReaper = null;
	}

	private static void start() throws IOException {
		Path home = classFolder();
		// Its input alone ties it to this process. Its own folder to work in: a process holds its folder open, and a
		// test's run folder has to be free to delete.
		reaper = new ProcessBuilder(List.of(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
				"-Xmx16m", "-Xss256k", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-cp", home.toString(), OrphanReaper.class.getName()))
				.directory(home.toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		toReaper = new OutputStreamWriter(reaper.getOutputStream(), StandardCharsets.US_ASCII);
	}

	/**
	 * A folder holding the reaper's class file and nothing else, named for the file's bytes: Gradle loads this plugin
	 * from a jar of its own keeping, which a second program shouldn't hold open, and builds of other checkouts may
	 * carry another version of the class.
	 */
	private static Path classFolder() throws IOException {
		String file = OrphanReaper.class.getName().replace('.', '/') + ".class";
		byte[] bytes;
		try (InputStream in = OrphanReaper.class.getResourceAsStream("/" + file)) {
			if (in == null) throw new IOException("No class file for " + OrphanReaper.class.getName());
			bytes = in.readAllBytes();
		}
		String digest;
		try {
			digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		Path home = Path.of(System.getProperty("java.io.tmpdir"), "oldfaithful-reaper-" + digest);
		Path target = home.resolve(file);
		if (Files.isRegularFile(target)) return home;
		Files.createDirectories(target.getParent());
		// Written beside its place and renamed in whole, never over one that's there: another build may be at the same step.
		Path made = Files.createTempFile(target.getParent(), "making-", ".tmp");
		try {
			Files.write(made, bytes);
			Files.move(made, target);
		} catch (IOException e) {
			// Another build's, and the same bytes.
			if (!Files.isRegularFile(target)) throw e;
		} finally {
			Files.deleteIfExists(made);
		}
		return home;
	}
}
