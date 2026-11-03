package org.studioquire.oldfaithful.saves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import net.minecraft.world.chunk.storage.RegionFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A writer process saving chunks as fast as it can is killed outright (TerminateProcess on Windows, SIGKILL elsewhere)
 * at random moments, again and again on the same region file; after every kill, vanilla's own reader must find every
 * entry complete and in its place. The same with vanilla's writer is the control: it shows the test catches torn
 * records when there are any.
 */
class KillTest {
	/** -Doldfaithful.kills=N (through the test task's system properties) for a longer run. */
	private static final int RUNS = Integer.getInteger("oldfaithful.kills", 30);
	/** The writer fills entries 0 to 255 before the first kill; after any kill, all of them must be there. */
	private static final int WRITTEN = 256;

	@TempDir
	Path tmp;

	@Test
	void killedMidSaveNeverLeavesADamagedRecord() throws Exception {
		Result r = run("ours", tmp.resolve("ours.mcr").toFile());
		System.out.println("[KillTest] ours: " + r);
		assertEquals(0, r.damaged, "damaged entries after kills: " + r.problems);
		assertTrue(r.entries > 100, "the writer got going: " + r);
	}

	@Test
	void vanillaControl() throws Exception {
		Result r = run("vanilla", tmp.resolve("vanilla.mcr").toFile());
		// Not asserted either way: the count shows whether these kills can catch a torn write at all.
		System.out.println("[KillTest] vanilla control: " + r);
	}

	static final class Result {
		int runs, entries, damaged;
		final List<String> problems = new ArrayList<>();

		@Override
		public String toString() {
			return runs + " kills, " + entries + " entries checked in total, " + damaged + " damaged"
					+ (problems.isEmpty() ? "" : " (first: " + problems.get(0) + ")");
		}
	}

	private Result run(String mode, File region) throws Exception {
		Result result = new Result();
		Random r = new Random(mode.hashCode());
		String java = ProcessHandle.current().info().command().orElse("java");
		for (int i = 0; i < RUNS; i++) {
			Process p = new ProcessBuilder(java, "-Xmx256m", "-cp", System.getProperty("java.class.path"), Writer.class.getName(),
					region.getAbsolutePath(), mode, String.valueOf(i)).redirectErrorStream(true).start();
			BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
			String line = out.readLine();
			if (!"writing".equals(line)) throw new AssertionError("writer didn't start: " + line);
			Thread.sleep(150 + r.nextInt(900));
			p.destroyForcibly();
			p.waitFor(30, TimeUnit.SECONDS);
			result.runs++;
			check(region, result);
			// Damaged entries would stay damaged; clear them so later kills are counted on their own.
			if (result.damaged > 0 && mode.equals("vanilla")) clearDamaged(region);
		}
		return result;
	}

	/**
	 * Every one of the 256 chunks the writer wrote before it was first killed must be there, complete and in place:
	 * a missing entry counts as damage. The file must be a whole number of sectors (plain b1.7.3's constructor zeroes
	 * the index of one that isn't; an unaligned file is counted, then padded at the end so the entries can be read).
	 */
	private static void check(File region, Result result) throws IOException {
		if (region.length() % 4096 != 0) {
			result.damaged++;
			if (result.problems.size() < 5) result.problems.add("kill " + result.runs + ": file left " + region.length() + " bytes");
			RegionWrites.alignBeforeOpen(region);
		}
		RegionFile vanilla = new RegionFile(region);
		try {
			for (int i = 0; i < WRITTEN; i++) {
				result.entries++;
				try {
					if (!vanilla.hasChunkData(i & 31, i >> 5)) throw new IOException("missing");
					DataInputStream in = vanilla.getChunkInputStream(i & 31, i >> 5);
					if (in == null) throw new IOException("vanilla can't read it");
					int[] iv = Payloads.check(in);
					if (iv[0] != i) throw new IOException("holds entry " + iv[0] + "'s record");
				} catch (IOException e) {
					result.damaged++;
					if (result.problems.size() < 5) result.problems.add("kill " + result.runs + ", entry " + i + ": " + e.getMessage());
				}
			}
		} finally {
			vanilla.close();
		}
	}

	/** The control's damage stays; rewrite damaged or missing entries cleanly so each kill is counted on its own. */
	private static void clearDamaged(File region) throws IOException {
		RegionFile vanilla = new RegionFile(region);
		List<Integer> bad = new ArrayList<>();
		try {
			for (int i = 0; i < WRITTEN; i++) {
				try {
					DataInputStream in = vanilla.hasChunkData(i & 31, i >> 5) ? vanilla.getChunkInputStream(i & 31, i >> 5) : null;
					if (in == null || Payloads.check(in)[0] != i) bad.add(i);
				} catch (IOException e) {
					bad.add(i);
				}
			}
			for (int i : bad) {
				DataOutputStream out = vanilla.getChunkOutputStream(i & 31, i >> 5);
				out.write(Payloads.plain(i, 0, 500));
				out.close();
			}
		} finally {
			vanilla.close();
		}
	}

	/** The child process: saves chunks until it's killed. */
	public static final class Writer {
		public static void main(String[] args) throws Exception {
			File region = new File(args[0]);
			boolean ours = args[1].equals("ours");
			Random r = new Random(Long.parseLong(args[2]) * 7919);
			// Payloads made up front, so nearly all the writer's time is spent saving, where a kill does damage.
			byte[][] plain = new byte[256 * 2][];
			byte[][] packed = new byte[plain.length][];
			for (int k = 0; k < plain.length; k++) {
				int index = k >> 1;
				plain[k] = Payloads.plain(index, k & 1, 100 + r.nextInt((k & 1) == 0 ? 3000 : 30000));
				packed[k] = Payloads.deflate(plain[k]);
			}
			boolean fresh = !region.exists() || region.length() == 0;
			if (ours) {
				RegionWrites.alignBeforeOpen(region); // as the game does
				RandomAccessFile raf = new RandomAccessFile(region, "rw");
				RegionWrites w = RegionWrites.openForTest(region, new int[1024], new int[1024], new ArrayList<>(), raf);
				if (fresh) {
					for (int i = 0; i < WRITTEN; i++) {
						byte[] c = Payloads.deflate(Payloads.plain(i, 0, 500));
						w.write(i, c, c.length);
					}
					while (w.commitOnce()) {
					}
				}
				System.out.println("writing");
				System.out.flush();
				while (true) {
					int k = r.nextInt(packed.length);
					w.write(k >> 1, packed[k], packed[k].length);
				}
			} else {
				RegionFile vanilla = new RegionFile(region);
				if (fresh) {
					for (int i = 0; i < WRITTEN; i++) {
						DataOutputStream out = vanilla.getChunkOutputStream(i & 31, i >> 5);
						out.write(Payloads.plain(i, 0, 500));
						out.close();
					}
				}
				System.out.println("writing");
				System.out.flush();
				// Vanilla's own write of an already compressed record (what its output stream calls on close).
				java.lang.reflect.Method write = RegionFile.class.getDeclaredMethod("writeChunkData", int.class, int.class, byte[].class, int.class);
				write.setAccessible(true);
				while (true) {
					int k = r.nextInt(packed.length);
					write.invoke(vanilla, (k >> 1) & 31, (k >> 1) >> 5, packed[k], packed[k].length);
				}
			}
		}
	}
}
