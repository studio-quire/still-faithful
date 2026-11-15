package org.studioquire.oldfaithful.polish;

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.WorldProperties;
import net.minecraft.world.chunk.storage.RegionFile;

/**
 * #14: chunk saves compressed and written on a thread of their own, so the autosave every 40 ticks (and the saves of
 * chunks unloading) no longer stall the game. What's saved, and when, is the game's own: the chunk is turned into
 * its save data on the game thread at the moment beta saves it (so a save is the same snapshot of the world it was,
 * chunks saved in one tick together), and only the compressing and the write move. They start at once, so a save is
 * on disk at the same moment it would have been (beta stops the game while writing; here the game goes on meanwhile),
 * and then as durable as Pure Faithful's saves mod makes it.
 * <p>
 * Safe with the saves mod (required: without it this isn't applied, PolishConfig), because:
 * <ul>
 * <li>every chunk save goes through this one queue, in order, one writer: a later save of a chunk can never be
 * overtaken by an earlier one;</li>
 * <li>a chunk being loaded waits for its own queued save first, so it never reads an older record;</li>
 * <li>the region file a save goes to is taken on the game thread when it's queued, and held until written (so the
 * game's cache of open files, which holds them softly, can't drop it and open a second copy), and every closing of
 * region files ({@code RegionIo.flush}) waits for the queue to empty first;</li>
 * <li>all reading and writing of a region file is under its own lock (vanilla's synchronized methods, which the saves
 * mod's crash-safe writer also uses), so the writer and a load never interleave;</li>
 * <li>a full save (leaving the world, save-all, stopping a server) waits for the queue in the storage's flush, before
 * the saves mod forces it all to disk;</li>
 * <li>a hard kill mid-write is a crash mid-write, which the saves mod's writer leaves harmless (the previous record
 * stays), and a shutdown waits for the queue (up to 10 s).</li>
 * </ul>
 * The queue holds at most {@link #CAPACITY} saves; past that the game thread waits, as beta always did.
 * <p>
 * Nothing stops the writer: it takes no notice of interrupts, and a failed save, however it fails, is reported and the
 * next one written. Should it end all the same, a thread waiting on it finds out within {@link #CHECK_MS} and writes
 * the queued saves itself, one at a time in the queue's order, as the writer would have; from then on each save is
 * written by the thread that queues it. The same holds from the start if the JVM is already exiting when the first
 * save is queued (from another shutdown hook): a writer started then could be cut off with saves still queued.
 */
public final class BackgroundSaves {
	static final int CAPACITY = 256;
	/** How often a thread waiting on the writer checks that it's still there. */
	static final long CHECK_MS = 100;
	private static final Object LOCK = new Object();
	private static final ArrayDeque<Task> queue = new ArrayDeque<>();
	private static final Map<String, Integer> pending = new HashMap<>();
	/** The save being written, and the thread writing it: the writer, or one standing in for it. */
	private static Task writing;
	private static Thread writingOn;
	private static Thread writer;
	/** Saves written by the writer thread so far (for the self-test). */
	public static volatile long written;

	private static final class Task {
		final RegionFile region;
		final int localX, localZ;
		final byte[] data;
		final String key;
		final WorldProperties properties;

		Task(RegionFile region, int localX, int localZ, byte[] data, String key, WorldProperties properties) {
			this.region = region;
			this.localX = localX;
			this.localZ = localZ;
			this.data = data;
			this.key = key;
			this.properties = properties;
		}
	}

	private BackgroundSaves() {
	}

	public static String key(File dir, int chunkX, int chunkZ) {
		return dir.getAbsolutePath() + ":" + chunkX + "," + chunkZ;
	}

	/** Queues a chunk's save data (uncompressed NBT) for its region file. Waits while the queue is full. */
	public static void enqueue(RegionFile region, File dir, int chunkX, int chunkZ, byte[] data, WorldProperties properties) {
		Task task = new Task(region, chunkX & 31, chunkZ & 31, data, key(dir, chunkX, chunkZ), properties);
		synchronized (LOCK) {
			start();
			while (queue.size() >= CAPACITY && writer.isAlive()) waitOn();
			queue.addLast(task);
			pending.merge(task.key, 1, Integer::sum);
			LOCK.notifyAll();
		}
		if (!writer.isAlive()) await(BackgroundSaves::idle);
	}

	/** Before a chunk is read from disk: waits until any queued save of it is written. */
	public static void awaitChunk(File dir, int chunkX, int chunkZ) {
		if (Thread.currentThread() == writer) return;
		String key = key(dir, chunkX, chunkZ);
		await(() -> !pending.containsKey(key));
	}

	/** Waits until every queued save is written. */
	public static void drain() {
		if (Thread.currentThread() == writer) return;
		await(BackgroundSaves::idle);
	}

	private static boolean idle() {
		return queue.isEmpty() && writing == null;
	}

	private interface Condition {
		/** Asked holding LOCK. */
		boolean holds();
	}

	private static void await(Condition done) {
		try {
			await(done, 0);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted waiting for chunk saves", e);
		}
	}

	/**
	 * Waits until {@code done} holds, or until {@code until} (milliseconds, 0 for no limit; false then). While the
	 * writer isn't running, this thread writes the queue in its place, all of it.
	 */
	private static boolean await(Condition done, long until) throws InterruptedException {
		while (true) {
			Task task = null;
			synchronized (LOCK) {
				// A wait reached from inside a save this thread is writing: the writer's own returns at once too.
				if (writingOn == Thread.currentThread()) return true;
				boolean late = until != 0 && System.currentTimeMillis() >= until;
				if (!late && (writer == null || !writer.isAlive())) task = standIn();
				if (task == null) {
					if (done.holds()) return true;
					if (late) return false;
					LOCK.wait(CHECK_MS);
					continue;
				}
			}
			writeTaken(task);
		}
	}

	private static void waitOn() {
		try {
			LOCK.wait(CHECK_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted waiting for chunk saves", e);
		}
	}

	private static void start() {
		if (writer != null) return;
		writer = new Thread(BackgroundSaves::loop, "Still Faithful chunk writer");
		writer.setDaemon(true);
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(BackgroundSaves::atExit, "Still Faithful chunk writer (exit)"));
		} catch (IllegalStateException exiting) {
			// The JVM is already exiting: no writer, so each save is written by the thread that queues it.
			return;
		}
		writer.start();
	}

	private static void loop() {
		while (true) {
			Task task;
			synchronized (LOCK) {
				while (queue.isEmpty()) {
					try {
						LOCK.wait();
					} catch (InterruptedException ignored) {
						// Nothing stops the writer: a save queued is a save written.
					}
				}
				task = take();
			}
			writeTaken(task);
		}
	}

	/** Holding LOCK: the queue's next save for a thread standing in for the writer, or null if there's none for it. */
	private static Task standIn() {
		if (writing != null) {
			if (writingOn.isAlive()) return null;
			// Its thread ended after the write was tried and before it was marked done: only the marking is left.
			done(writing);
		}
		return take();
	}

	/** Holding LOCK: the queue's next save, now this thread's to write, or null if the queue is empty. */
	private static Task take() {
		Task task = queue.pollFirst();
		if (task != null) {
			writing = task;
			writingOn = Thread.currentThread();
		}
		return task;
	}

	/** Holding LOCK: a save is written, or failed. */
	private static void done(Task task) {
		writing = null;
		writingOn = null;
		pending.computeIfPresent(task.key, (k, n) -> n > 1 ? n - 1 : null);
		LOCK.notifyAll();
	}

	private static void writeTaken(Task task) {
		try {
			write(task);
		} catch (Throwable t) {
			report(task, t);
		} finally {
			synchronized (LOCK) {
				done(task);
			}
		}
	}

	private static void report(Task task, Throwable t) {
		try {
			// As beta's own save does with a failure: say so and go on with the next.
			System.out.println("[stillfaithful-polish] WARN A chunk save failed (" + task.key + "): " + t);
			t.printStackTrace(System.out);
		} catch (Throwable ignored) {
			// Saying so failed too (out of memory, say): going on matters more.
		}
	}

	private static void write(Task task) throws Exception {
		DataOutputStream out = task.region.getChunkOutputStream(task.localX, task.localZ);
		out.write(task.data);
		out.close();
		int bytes = task.region.resetBytesWritten();
		WorldProperties p = task.properties;
		synchronized (p) {
			p.setSizeOnDisk(p.getSizeOnDisk() + bytes);
		}
		if (Thread.currentThread() == writer) BackgroundSaves.written++;
	}

	private static void atExit() {
		try {
			await(BackgroundSaves::idle, System.currentTimeMillis() + 10_000);
		} catch (InterruptedException ignored) {
			// An interrupted exit stops waiting.
		}
	}
}
