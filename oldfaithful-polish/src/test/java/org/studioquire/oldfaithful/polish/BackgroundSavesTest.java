package org.studioquire.oldfaithful.polish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;

import net.minecraft.world.WorldProperties;
import net.minecraft.world.chunk.storage.RegionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #14 without its writer: as when the writer thread has ended, or was never started because the JVM was already
 * exiting. The queued saves are written by the threads that queue them or wait on them, in the queue's order, and no
 * wait goes on forever. (The only test here that touches BackgroundSaves: it puts an ended thread in as the writer
 * before anything starts a real one.)
 */
class BackgroundSavesTest {
	@TempDir
	File dir;

	@Test
	void savesAreWrittenWithoutTheWriter() throws Exception {
		Thread ended = new Thread(() -> {
		});
		ended.start();
		ended.join();
		set("writer", ended);

		RegionFile region = new RegionFile(new File(dir, "r.0.0.mcr"));
		WorldProperties properties = new WorldProperties(0, "test");
		long before = BackgroundSaves.written;
		assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
			// Queued while the writer was there, and left when it ended: the next thread to wait writes them all, oldest
			// first, so a chunk saved twice ends with its later save.
			queued(region, properties, 1, 1, data(1));
			queued(region, properties, 1, 1, data(2));
			queued(region, properties, 2, 2, data(3));
			queued(region, properties, 3, 3, data(4));
			BackgroundSaves.awaitChunk(dir, 1, 1);
			assertArrayEquals(data(2), read(region, 1, 1));
			assertArrayEquals(data(3), read(region, 2, 2));
			assertArrayEquals(data(4), read(region, 3, 3));
			assertEquals(0, queue().size());

			// Queued now: written by the time enqueue returns.
			BackgroundSaves.enqueue(region, dir, 4, 4, data(5), properties);
			assertArrayEquals(data(5), read(region, 4, 4));

			// A save another thread took and never marked done before it ended: waiting for that chunk, or for all,
			// still ends, and what was queued behind it is written.
			set("writing", task(region, properties, 5, 5, data(6)));
			set("writingOn", ended);
			pending().merge(BackgroundSaves.key(dir, 5, 5), 1, Integer::sum);
			queued(region, properties, 6, 6, data(7));
			BackgroundSaves.awaitChunk(dir, 5, 5);
			BackgroundSaves.drain();
			assertArrayEquals(data(7), read(region, 6, 6));
			assertEquals(0, pending().size());
		});
		region.close();
		// None of them by a writer thread.
		assertEquals(before, BackgroundSaves.written);
		assertTrue(properties.getSizeOnDisk() > 0);
	}

	private static byte[] data(int n) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < 300; i++) s.append("save ").append(n).append(' ');
		return s.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] read(RegionFile region, int x, int z) throws Exception {
		try (DataInputStream in = region.getChunkInputStream(x & 31, z & 31)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
			return out.toByteArray();
		}
	}

	/** A save in the queue, put there as enqueue would, without anything writing it. */
	private void queued(RegionFile region, WorldProperties properties, int x, int z, byte[] data) throws Exception {
		queue().addLast(task(region, properties, x, z, data));
		pending().merge(BackgroundSaves.key(dir, x, z), 1, Integer::sum);
	}

	private Object task(RegionFile region, WorldProperties properties, int x, int z, byte[] data) throws Exception {
		Constructor<?> c = Class.forName(BackgroundSaves.class.getName() + "$Task")
				.getDeclaredConstructor(RegionFile.class, int.class, int.class, byte[].class, String.class, WorldProperties.class);
		c.setAccessible(true);
		return c.newInstance(region, x & 31, z & 31, data, BackgroundSaves.key(dir, x, z), properties);
	}

	@SuppressWarnings("unchecked")
	private static ArrayDeque<Object> queue() throws Exception {
		return (ArrayDeque<Object>) field("queue").get(null);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Integer> pending() throws Exception {
		return (Map<String, Integer>) field("pending").get(null);
	}

	private static void set(String name, Object value) throws Exception {
		field(name).set(null, value);
	}

	private static Field field(String name) throws Exception {
		Field f = BackgroundSaves.class.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}
}
