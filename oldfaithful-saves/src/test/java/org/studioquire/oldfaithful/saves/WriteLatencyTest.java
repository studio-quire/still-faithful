package org.studioquire.oldfaithful.saves;

import java.io.DataOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import net.minecraft.world.chunk.storage.RegionFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a chunk save costs the game thread, ours against vanilla, in the game's rhythm (autosave passes of 24 chunks
 * every 2 s, compressed to the sizes real chunks have), while our committer forces the file in the background.
 * A measurement, not a pass/fail check: {@code ./gw :oldfaithful-saves:test -Pmeasure}.
 */
@Tag("measure")
class WriteLatencyTest {
	@TempDir
	Path tmp;

	@Test
	void gameThreadCostPerChunkSave() throws Exception {
		for (int round = 0; round < 2; round++) {
			report("vanilla", vanilla(tmp.resolve("v" + round + ".mcr").toFile()));
			report("ours", ours(tmp.resolve("o" + round + ".mcr").toFile()));
		}
	}

	private static final int PASSES = 40, PER_PASS = 24, GAP_MS = 100;

	private long[] vanilla(File f) throws Exception {
		RegionFile region = new RegionFile(f);
		Random r = new Random(1);
		long[] t = new long[PASSES * PER_PASS];
		int k = 0;
		for (int pass = 0; pass < PASSES; pass++) {
			for (int i = 0; i < PER_PASS; i++) {
				int index = r.nextInt(400);
				byte[] plain = Payloads.plain(index, pass, 20000 + r.nextInt(60000));
				long start = System.nanoTime();
				DataOutputStream out = region.getChunkOutputStream(index & 31, index >> 5);
				out.write(plain);
				out.close();
				t[k++] = System.nanoTime() - start;
			}
			Thread.sleep(GAP_MS);
		}
		region.close();
		return t;
	}

	private long[] ours(File f) throws Exception {
		RandomAccessFile raf = new RandomAccessFile(f, "rw");
		RegionWrites w = RegionWrites.openForTest(f, new int[1024], new int[1024], new ArrayList<>(), raf);
		Random r = new Random(1);
		long[] t = new long[PASSES * PER_PASS];
		int k = 0;
		for (int pass = 0; pass < PASSES; pass++) {
			for (int i = 0; i < PER_PASS; i++) {
				int index = r.nextInt(400);
				byte[] plain = Payloads.plain(index, pass, 20000 + r.nextInt(60000));
				long start = System.nanoTime();
				// The same work vanilla's stream does: compress, then write.
				byte[] c = Payloads.deflate(plain);
				w.write(index, c, c.length);
				t[k++] = System.nanoTime() - start;
			}
			Thread.sleep(GAP_MS);
		}
		w.commitAndClose();
		raf.close();
		return t;
	}

	private static void report(String name, long[] t) {
		long[] s = t.clone();
		Arrays.sort(s);
		System.out.printf("[WriteLatency] %-7s per chunk save: median %.3f ms, p99 %.3f ms, max %.3f ms, total %.1f ms%n", name,
				s[s.length / 2] / 1e6, s[s.length * 99 / 100] / 1e6, s[s.length - 1] / 1e6, Arrays.stream(t).sum() / 1e6);
	}
}
