package org.studioquire.oldfaithful.saves;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Whole-file saves, the committer's handling of them, and what happens when a copy of a damaged original can't be kept. */
class FileSafetyTest {
	@TempDir
	Path tmp;

	@AfterEach
	void unpause() {
		Committer.paused = false;
	}

	/** A job that records when it ran, and can be held until released. */
	static final class Probe extends Committer.Job {
		final CountDownLatch started = new CountDownLatch(1), release;
		volatile long start, end;

		Probe(boolean hold) {
			release = new CountDownLatch(hold ? 1 : 0);
		}

		@Override
		void run() throws IOException {
			start = System.nanoTime();
			started.countDown();
			try {
				release.await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
			end = System.nanoTime();
		}
	}

	/**
	 * While one thread is saving a file, another's commitAll must neither start a second save of the same file nor
	 * return before the first is done (the game's full save, then a backup tool, relies on that).
	 */
	@Test
	void neverTwoSavesOfOneFileAtOnce() throws Exception {
		Committer.paused = true;
		File target = tmp.resolve("level.dat").toFile();
		Probe first = new Probe(true), second = new Probe(false);
		Committer.enqueue(target, first);
		Thread a = new Thread(Committer::commitAll);
		a.start();
		assertTrue(first.started.await(10, TimeUnit.SECONDS));
		Committer.enqueue(target, second);
		long[] bReturned = new long[1];
		Thread b = new Thread(() -> {
			Committer.commitAll();
			bReturned[0] = System.nanoTime();
		});
		b.start();
		Thread.sleep(300);
		assertEquals(0, second.start, "a second save of the same file started while the first was running");
		assertTrue(b.isAlive(), "commitAll returned while a save was still in flight");
		first.release.countDown();
		a.join(10_000);
		b.join(10_000);
		assertTrue(second.start >= first.end, "the second save overlapped the first");
		assertTrue(bReturned[0] >= second.end, "commitAll returned before the second save finished");
	}

	/** Two whole-file saves of one target, one after the other: the newer wins, and no temporary file is left. */
	@Test
	void newerSaveWins() throws Exception {
		Committer.paused = true;
		File target = tmp.resolve("p.dat").toFile();
		SafeFiles.replaceLater(target, "old".getBytes());
		Committer.commitAll();
		SafeFiles.replaceLater(target, "older".getBytes());
		SafeFiles.replaceLater(target, "newest".getBytes());
		Committer.commitAll();
		assertArrayEquals("newest".getBytes(), Files.readAllBytes(target.toPath()));
		assertEquals(0, leftovers());
	}

	/**
	 * Map files: the game's NbtIo closes the stream in a finally, so a save that throws halfway still closes it. A
	 * stream closed on incomplete NBT, or after a failed write, must never replace the file.
	 */
	@Test
	void anIncompleteMapSaveNeverReplacesTheFile() throws Exception {
		Committer.paused = true;
		File map = tmp.resolve("map_0.dat").toFile();
		byte[] good = gzipNbt(1);
		Files.write(map.toPath(), good);

		byte[] next = gzipNbt(2);
		FileOutputStream half = SafeFiles.replacingStream(map);
		half.write(next, 0, next.length / 2);
		half.close();
		Committer.commitAll();
		assertArrayEquals(good, Files.readAllBytes(map.toPath()), "a half-written map replaced the good one");

		FileOutputStream whole = SafeFiles.replacingStream(map);
		whole.write(next);
		whole.close();
		Committer.commitAll();
		assertArrayEquals(next, Files.readAllBytes(map.toPath()));
		assertEquals(0, leftovers());

		// idcounts.dat is plain NBT, not gzipped: the same holds.
		File ids = tmp.resolve("idcounts.dat").toFile();
		byte[] plain = plainNbt(3);
		Files.write(ids.toPath(), plain);
		FileOutputStream cut = SafeFiles.replacingStream(ids);
		byte[] plain4 = plainNbt(4);
		cut.write(plain4, 0, plain4.length - 3);
		cut.close();
		Committer.commitAll();
		assertArrayEquals(plain, Files.readAllBytes(ids.toPath()));
	}

	/** A chunk whose record can't be read, and whose copy can't be kept, must never be saved over. */
	@Test
	void aChunkWhoseCopyFailsIsNeverSavedOver() throws IOException {
		File dir = tmp.resolve("world").toFile();
		dir.mkdirs();
		// corrupt/ can't be made: a file is in its place.
		Files.write(new File(dir, "corrupt").toPath(), new byte[1]);
		ChunkLoad.lost(dir, 5, 7, new byte[] {1, 2, 3}, "a test", new File(dir, "region/r.0.0.mcr"));
		assertTrue(ChunkLoad.isProtected(dir, 5, 7));

		File ok = tmp.resolve("world2").toFile();
		ok.mkdirs();
		ChunkLoad.lost(ok, 5, 7, new byte[] {1, 2, 3}, "a test", new File(ok, "region/r.0.0.mcr"));
		assertFalse(ChunkLoad.isProtected(ok, 5, 7), "a copy was kept, so the chunk may be regenerated and saved");
		try (Stream<Path> files = Files.list(ok.toPath().resolve("corrupt"))) {
			assertTrue(files.anyMatch(p -> p.toString().endsWith(".record")));
		}
	}

	/** The same for a player's file and for level.dat. */
	@Test
	void aFileWhoseCopyFailsIsNeverSavedOver() throws IOException {
		File world = tmp.resolve("w").toFile();
		new File(world, "players").mkdirs();
		Files.write(new File(world, "corrupt").toPath(), new byte[1]);
		File player = new File(world, "players/Alex.dat");
		Files.write(player.toPath(), new byte[] {9, 9, 9});
		WorldFiles.playerUnreadable(world, player, "Alex");
		assertTrue(WorldFiles.isProtected(player));

		Files.write(new File(world, "level.dat").toPath(), new byte[] {7});
		WorldFiles.levelUnreadable(world);
		assertTrue(WorldFiles.isProtected(new File(world, "level.dat")));

		File world2 = tmp.resolve("w2").toFile();
		new File(world2, "players").mkdirs();
		File player2 = new File(world2, "players/Steve.dat");
		Files.write(player2.toPath(), new byte[] {9, 9, 9});
		WorldFiles.playerUnreadable(world2, player2, "Steve");
		assertFalse(WorldFiles.isProtected(player2));
	}

	@Test
	void nbtFileCheck() throws IOException {
		File f = tmp.resolve("x.dat").toFile();
		Files.write(f.toPath(), gzipNbt(5));
		assertNull(SafeFiles.checkNbtFile(f));
		byte[] g = gzipNbt(5);
		Files.write(f.toPath(), Arrays.copyOf(g, g.length - 4));
		assertNotNull(SafeFiles.checkNbtFile(f));
	}

	private long leftovers() throws IOException {
		try (Stream<Path> files = Files.walk(tmp)) {
			return files.filter(p -> p.toString().endsWith(".oftmp")).count();
		}
	}

	private static NbtCompound sample(int n) {
		NbtCompound data = new NbtCompound();
		data.putInt("scale", n);
		data.putByteArray("colors", new byte[16384]);
		NbtCompound root = new NbtCompound();
		root.put("data", data);
		return root;
	}

	private static byte[] gzipNbt(int n) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(sample(n), out);
		return out.toByteArray();
	}

	private static byte[] plainNbt(int n) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.write(sample(n), new DataOutputStream(out));
		return out.toByteArray();
	}
}
