package oldfaithful.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Folders that another build, in another process, may be reading or clearing at the same moment. */
public final class Folders {
	private Folders() {
	}

	/**
	 * Brings {@code place} to hold exactly what {@code made} holds, and removes {@code made}. A file that is already the
	 * same is left untouched, and one that differs is replaced whole by a rename, so a build copying {@code place}
	 * meanwhile reads each file as it was or as it will be, never half written. What no longer belongs goes last: a
	 * reader may find a file too many for a moment, never one too few. Both folders on one volume.
	 */
	public static void settle(Path made, Path place) throws IOException {
		Set<Path> wanted = new HashSet<>();
		List<Path> entries;
		try (Stream<Path> walk = Files.walk(made)) {
			entries = walk.toList();
		}
		for (Path p : entries) {
			Path target = place.resolve(made.relativize(p).toString());
			wanted.add(target);
			if (Files.isDirectory(p)) {
				Files.createDirectories(target);
			} else if (!Files.isRegularFile(target) || Files.mismatch(p, target) != -1) {
				patiently(() -> Files.move(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE), target);
			}
		}
		List<Path> held;
		try (Stream<Path> walk = Files.walk(place)) {
			held = walk.sorted(Comparator.reverseOrder()).toList();
		}
		for (Path p : held) {
			if (!wanted.contains(p)) patiently(() -> Files.deleteIfExists(p), p);
		}
		delete(made);
	}

	private interface Step {
		void run() throws IOException;
	}

	/** Windows refuses to replace or remove a file some programs have open, so a reader gets a few seconds to finish. */
	private static void patiently(Step step, Path what) throws IOException {
		for (int attempt = 0;; attempt++) {
			try {
				step.run();
				return;
			} catch (IOException e) {
				if (attempt == 50) throw new IOException(what + " can't be replaced: is a game or another build still reading it?", e);
				try {
					Thread.sleep(200);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	/**
	 * Clears what builds that were killed half way left in {@code parent}: the folders named {@code prefix}... that
	 * nothing has touched for {@code age}. As far as it goes: another build may be clearing the same ones.
	 */
	public static void sweep(Path parent, String prefix, Duration age) {
		Instant old = Instant.now().minus(age);
		try (Stream<Path> entries = Files.list(parent)) {
			for (Path p : entries.filter(p -> p.getFileName().toString().startsWith(prefix)).toList()) {
				try {
					if (Files.getLastModifiedTime(p).toInstant().isBefore(old)) delete(p);
				} catch (IOException gone) {
					// Another build swept it first.
				}
			}
		} catch (IOException | UncheckedIOException e) {
			// No such folder yet, or it changed under the listing: a later sweep's.
		}
	}

	/** Removes a folder and all in it, as far as it goes: what's left (a file still open, another build at the same folder) is a later sweep's. */
	public static void delete(Path dir) {
		try (Stream<Path> files = Files.walk(dir)) {
			for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		} catch (IOException | UncheckedIOException e) {
			// Files.walk reports a folder that went away under it unchecked.
		}
	}
}
