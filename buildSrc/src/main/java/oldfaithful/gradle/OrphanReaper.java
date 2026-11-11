package oldfaithful.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A small program of its own, started by {@link Orphans}: reads processes from its input, one a line (its id and when
 * it started, as {@link #line} writes them), and when the input ends, ends every one of those processes still running,
 * with all they started. The input is a pipe from the build's process, and the system closes it when that process
 * goes, however it goes: nothing is asked of a build that was killed.
 *
 * <p>It ends nothing it can't tell is the build's: the system gives a process id out again once its process is gone,
 * so a process is known by its id and its start together, here and in everything it is asked through.
 *
 * <p>One class file, using only the JDK: {@link Orphans} runs it from a copy of this file alone.
 */
public final class OrphanReaper {
	private OrphanReaper() {
	}

	public static void main(String[] args) {
		Map<Long, ProcessHandle> watched = new ConcurrentHashMap<>();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII))) {
			String line;
			while ((line = in.readLine()) != null) {
				String[] words = line.trim().split(" ");
				if (words.length != 2) continue;
				long pid;
				long start;
				try {
					pid = Long.parseLong(words[0]);
					start = Long.parseLong(words[1]);
				} catch (NumberFormatException e) {
					continue;
				}
				// The process the build started, not a later one given its id: one that ended before this line was read.
				ProcessHandle.of(pid).filter(handle -> start(handle) == start).ifPresent(handle -> {
					watched.put(pid, handle);
					handle.onExit().thenRun(() -> watched.remove(pid, handle));
				});
			}
		} catch (IOException e) {
			// The pipe broke: the same as its end.
		}
		for (ProcessHandle handle : watched.values()) {
			// Listed before its own end, after which what it started can't be found through it.
			List<ProcessHandle> started = started(handle);
			handle.destroyForcibly();
			started.forEach(ProcessHandle::destroyForcibly);
		}
	}

	/** How {@code process} is named to this program: its id and its start, or nothing where the system won't say when it started. */
	static String line(ProcessHandle process) {
		long start = start(process);
		return start < 0 ? "" : process.pid() + " " + start + "\n";
	}

	private static long start(ProcessHandle process) {
		return process.info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
	}

	/**
	 * The processes {@code root} started and those they started, as far as they can be told to be its own; none if it
	 * has ended. Not {@link ProcessHandle#descendants()}, which looks {@code root} up by its id alone: for one that has
	 * ended it gives every process whose parent had that id, whenever that was, and Windows leaves a process its
	 * parent's id after the parent is gone and the id is another's. A game that had ended by itself and was then closed
	 * could so have taken a program with it that had nothing to do with the build. Here a process's children are those
	 * started since it was ({@link ProcessHandle#children()} goes by the start it was found with), and they count only
	 * if it was still running after they were listed, so the id was its own throughout.
	 */
	static List<ProcessHandle> started(ProcessHandle root) {
		List<ProcessHandle> found = new ArrayList<>();
		List<ProcessHandle> children = root.children().toList();
		if (!root.isAlive()) return found;
		for (ProcessHandle child : children) {
			found.add(child);
			found.addAll(started(child));
		}
		return found;
	}
}
