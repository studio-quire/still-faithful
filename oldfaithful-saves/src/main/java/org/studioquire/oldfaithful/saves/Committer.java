package org.studioquire.oldfaithful.saves;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gets saves onto the disk durably without the game thread waiting for it: region commits ({@link RegionWrites}) and
 * whole-file replacements ({@link SafeFiles}) run on one background thread a moment after the writes that need them
 * (so a save pass's writes share one force), synchronously at the end of a full save ({@link #commitAll}), and from a
 * shutdown hook that keeps going until saving has been quiet for a moment, so a save still running in another hook
 * (the server's) gets committed too.
 *
 * <p>Locks: a region's writer calls {@link #dirty} while holding the region's monitor (vanilla's synchronized
 * writeChunkData), so {@code LOCK} is never held while taking a region's monitor.
 */
public final class Committer {
	private static final long DEBOUNCE_MS = 100;
	private static final long QUIET_MS = 300;
	private static final long SHUTDOWN_LIMIT_MS = 30_000;

	abstract static class Job {
		private int attempts;

		abstract void run() throws IOException;

		/** A newer job for the same file replaced this one before it ran. */
		void discard() {
		}

		/** Counts attempts; a job that keeps failing is given up after a few. */
		final int attempt() {
			return ++attempts;
		}
	}

	private static final Object LOCK = new Object();
	private static final Set<RegionWrites> regions = new LinkedHashSet<>();
	private static final Map<File, Job> jobs = new LinkedHashMap<>();
	private static final Set<File> inFlight = new HashSet<>();
	private static long lastActivity;
	private static boolean started;
	private static long lastErrorLog;
	/** Tests hold the background thread still to look at the files as a crash would leave them. */
	static volatile boolean paused;

	private Committer() {
	}

	static void dirty(RegionWrites region) {
		synchronized (LOCK) {
			regions.add(region);
			activity();
		}
	}

	static void forget(RegionWrites region) {
		synchronized (LOCK) {
			regions.remove(region);
		}
	}

	static void enqueue(File target, Job job) {
		synchronized (LOCK) {
			Job older = jobs.put(target.getAbsoluteFile(), job);
			if (older != null) older.discard();
			activity();
		}
	}

	/** Runs the queued write for {@code target}, if any, now on this thread (before the game reads that file). */
	static void flushFile(File target) {
		File key = target.getAbsoluteFile();
		Job job;
		synchronized (LOCK) {
			while (inFlight.contains(key)) waitQuietly(LOCK, 0);
			job = jobs.remove(key);
			if (job == null) return;
			inFlight.add(key);
		}
		runJob(key, job);
	}

	/**
	 * Everything written so far is durable when this returns (end of a full save), including a save another thread
	 * is running right now. Gives up only if saving keeps failing for a minute.
	 */
	public static void commitAll() {
		long deadline = System.nanoTime() + 60_000_000_000L;
		while (System.nanoTime() < deadline) {
			boolean ok = drain();
			synchronized (LOCK) {
				if (regions.isEmpty() && jobs.isEmpty() && inFlight.isEmpty()) return;
				// Only another thread's job left in flight, or one that failed and was queued again: wait a moment.
				waitQuietly(LOCK, ok ? 20 : 250);
			}
		}
		Log.warn("Saving didn't finish within a minute of a full save; it carries on in the background");
	}

	private static void activity() {
		lastActivity = System.nanoTime();
		if (!started) {
			started = true;
			Thread worker = new Thread(Committer::loop, "Pure Faithful save committer");
			worker.setDaemon(true);
			worker.start();
			Runtime.getRuntime().addShutdownHook(new Thread(Committer::shutdown, "Pure Faithful save committer (exit)"));
		}
		LOCK.notifyAll();
	}

	private static void loop() {
		while (true) {
			synchronized (LOCK) {
				while (regions.isEmpty() && jobs.isEmpty()) waitQuietly(LOCK, 0);
			}
			sleepQuietly(DEBOUNCE_MS);
			if (paused) continue;
			if (!drain()) sleepQuietly(1000);
		}
	}

	private static void shutdown() {
		long deadline = System.nanoTime() + SHUTDOWN_LIMIT_MS * 1_000_000L;
		while (System.nanoTime() < deadline) {
			drain();
			synchronized (LOCK) {
				boolean idle = regions.isEmpty() && jobs.isEmpty() && inFlight.isEmpty();
				if (idle && System.nanoTime() - lastActivity > QUIET_MS * 1_000_000L) return;
			}
			sleepQuietly(50);
		}
		Log.warn("Saving was still busy " + SHUTDOWN_LIMIT_MS / 1000 + " s after the game began to exit; stopped waiting");
	}

	/** One pass over everything pending. False if something failed (the caller backs off). */
	private static boolean drain() {
		boolean ok = true;
		while (true) {
			File key;
			Job job;
			synchronized (LOCK) {
				// Never two saves of one file at once: a file whose save is running elsewhere waits for the next pass.
				Map.Entry<File, Job> next = null;
				for (Map.Entry<File, Job> e : jobs.entrySet()) {
					if (!inFlight.contains(e.getKey())) {
						next = e;
						break;
					}
				}
				if (next == null) break;
				key = next.getKey();
				job = next.getValue();
				jobs.remove(key);
				inFlight.add(key);
			}
			ok &= runJob(key, job);
		}
		List<RegionWrites> list;
		synchronized (LOCK) {
			list = new ArrayList<>(regions);
		}
		for (RegionWrites region : list) {
			try {
				for (int step = 0; step < 4 && region.commitOnce(); step++) {
				}
			} catch (IOException e) {
				ok = false;
				error("Could not commit " + region.file + " (will retry): " + e);
			}
			// The region's monitor is never taken while holding LOCK (a writer holds its monitor when it calls dirty()).
			long seen = region.writes;
			boolean pending = region.pending();
			synchronized (LOCK) {
				if (!pending && region.writes == seen) regions.remove(region);
			}
		}
		return ok;
	}

	private static boolean runJob(File key, Job job) {
		boolean ok = false;
		try {
			job.run();
			ok = true;
		} catch (IOException e) {
			error("Could not save " + key + " (will retry): " + e);
		} finally {
			synchronized (LOCK) {
				inFlight.remove(key);
				// A failed save is tried again later (a file another program holds open, say) unless a newer one came.
				if (!ok && job.attempt() < 5 && !jobs.containsKey(key)) jobs.put(key, job);
				LOCK.notifyAll();
			}
		}
		return ok;
	}

	private static void error(String message) {
		long now = System.nanoTime();
		if (now - lastErrorLog > 10_000_000_000L) {
			lastErrorLog = now;
			Log.warn(message);
		}
	}

	private static void waitQuietly(Object lock, long ms) {
		try {
			lock.wait(ms);
		} catch (InterruptedException ignored) {
			// Nothing here is cancellable: saves finish.
		}
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
			// As above.
		}
	}
}
