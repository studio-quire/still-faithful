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
 */
public final class BackgroundSaves {
	static final int CAPACITY = 256;
	private static final Object LOCK = new Object();
	private static final ArrayDeque<Task> queue = new ArrayDeque<>();
	private static final Map<String, Integer> pending = new HashMap<>();
	private static Task writing;
	private static Thread writer;
	/** Saves written by the writer so far (for the self-test). */
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
			while (queue.size() >= CAPACITY) waitOn();
			queue.addLast(task);
			pending.merge(task.key, 1, Integer::sum);
			LOCK.notifyAll();
		}
	}

	/** Before a chunk is read from disk: waits until any queued save of it is written. */
	public static void awaitChunk(File dir, int chunkX, int chunkZ) {
		if (Thread.currentThread() == writer) return;
		String key = key(dir, chunkX, chunkZ);
		synchronized (LOCK) {
			while (pending.containsKey(key)) waitOn();
		}
	}

	/** Waits until every queued save is written. */
	public static void drain() {
		if (Thread.currentThread() == writer) return;
		synchronized (LOCK) {
			while (!queue.isEmpty() || writing != null) waitOn();
		}
	}

	private static void waitOn() {
		try {
			LOCK.wait();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted waiting for chunk saves", e);
		}
	}

	private static void start() {
		if (writer != null) return;
		writer = new Thread(BackgroundSaves::loop, "Still Faithful chunk writer");
		writer.setDaemon(true);
		writer.start();
		Runtime.getRuntime().addShutdownHook(new Thread(BackgroundSaves::atExit, "Still Faithful chunk writer (exit)"));
	}

	private static void loop() {
		while (true) {
			Task task;
			synchronized (LOCK) {
				while (queue.isEmpty()) {
					try {
						LOCK.wait();
					} catch (InterruptedException e) {
						return;
					}
				}
				task = queue.pollFirst();
				writing = task;
			}
			try {
				write(task);
			} catch (Throwable t) {
				// As beta's own save does with a failure: say so and go on with the next.
				System.out.println("[stillfaithful-polish] WARN A chunk save failed (" + task.key + "): " + t);
				t.printStackTrace(System.out);
			} finally {
				synchronized (LOCK) {
					writing = null;
					pending.computeIfPresent(task.key, (k, n) -> n > 1 ? n - 1 : null);
					LOCK.notifyAll();
				}
			}
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
		BackgroundSaves.written++;
	}

	private static void atExit() {
		long until = System.currentTimeMillis() + 10_000;
		synchronized (LOCK) {
			while ((!queue.isEmpty() || writing != null) && System.currentTimeMillis() < until) {
				try {
					LOCK.wait(100);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}
}
