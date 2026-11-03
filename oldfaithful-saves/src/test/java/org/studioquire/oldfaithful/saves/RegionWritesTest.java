package org.studioquire.oldfaithful.saves;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.world.chunk.storage.RegionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The region writer, with vanilla's own RegionFile (the official game's code, unmodified: no mixins run in these tests)
 * as the reader that has to accept everything we write.
 */
class RegionWritesTest {
	@TempDir
	Path tmp;

	@AfterEach
	void unpause() {
		Committer.paused = false;
	}

	/** Our writer on a region file, opened as vanilla's constructor opens it. */
	static final class Ours implements AutoCloseable {
		final RandomAccessFile raf;
		final RegionWrites w;
		final int[] info = new int[1024], times = new int[1024];
		final ArrayList<Boolean> flags = new ArrayList<>();

		Ours(File f) throws IOException {
			RegionWrites.alignBeforeOpen(f); // as the game does before vanilla's constructor
			raf = new RandomAccessFile(f, "rw");
			w = RegionWrites.openForTest(f, info, times, flags, raf);
		}

		void put(int index, byte[] plain) throws IOException {
			byte[] c = Payloads.deflate(plain);
			assertTrue(w.write(index, c, c.length) >= 0);
		}

		@Override
		public void close() throws IOException {
			w.commitAndClose();
			raf.close();
		}

		/** The process dies here: nothing more reaches the file. */
		void abandon() throws IOException {
			Committer.forget(w);
			raf.close();
		}
	}

	private File region() {
		return tmp.resolve("r.0.0.mcr").toFile();
	}

	/** index -> version, as vanilla reads the file; fails on anything torn or misplaced. */
	static Map<Integer, Integer> readWithVanilla(File f) throws IOException {
		Map<Integer, Integer> found = new HashMap<>();
		// Our writer must never leave the file a partial sector long: plain b1.7.3's constructor would then zero the index.
		assertEquals(0, f.length() % 4096, "file left unaligned: " + f.length());
		RegionFile vanilla = new RegionFile(f);
		try {
			for (int i = 0; i < 1024; i++) {
				if (!vanilla.hasChunkData(i & 31, i >> 5)) continue;
				DataInputStream in = vanilla.getChunkInputStream(i & 31, i >> 5);
				assertNotNull(in, "vanilla can't read entry " + i);
				int[] iv = Payloads.check(in);
				assertEquals(i, iv[0], "entry " + i + " holds another chunk's record");
				found.put(i, iv[1]);
			}
		} finally {
			vanilla.close();
		}
		return found;
	}

	@Test
	void vanillaReadsWhatWeWrite() throws IOException {
		Random r = new Random(1);
		Map<Integer, Integer> latest = new HashMap<>();
		try (Ours ours = new Ours(region())) {
			for (int n = 0; n < 2000; n++) {
				int index = r.nextInt(300);
				int version = latest.getOrDefault(index, 0) + 1;
				// Sizes from a few bytes to several sectors, so records move, shrink and grow.
				ours.put(index, Payloads.plain(index, version, 100 + r.nextInt(r.nextBoolean() ? 3000 : 40000)));
				latest.put(index, version);
			}
		}
		assertEquals(latest, readWithVanilla(region()));
		assertEquals(0, region().length() % 4096, "the file is a whole number of sectors");
	}

	@Test
	void weContinueVanillasFiles() throws IOException {
		Map<Integer, Integer> latest = new HashMap<>();
		RegionFile vanilla = new RegionFile(region());
		for (int i = 0; i < 200; i++) {
			DataOutputStream out = vanilla.getChunkOutputStream(i & 31, i >> 5);
			out.write(Payloads.plain(i, 1, 200 + i * 37));
			out.close();
			latest.put(i, 1);
		}
		vanilla.close();
		Random r = new Random(2);
		try (Ours ours = new Ours(region())) {
			for (int n = 0; n < 500; n++) {
				int index = r.nextInt(250);
				int version = latest.getOrDefault(index, 0) + 1;
				ours.put(index, Payloads.plain(index, version, 50 + r.nextInt(20000)));
				latest.put(index, version);
			}
		}
		assertEquals(latest, readWithVanilla(region()));
	}

	@Test
	void anUncommittedSaveLeavesTheOldRecord() throws IOException {
		try (Ours ours = new Ours(region())) {
			for (int i = 0; i < 50; i++) ours.put(i, Payloads.plain(i, 1, 5000));
		}
		Committer.paused = true;
		Ours ours = new Ours(region());
		for (int i = 0; i < 50; i++) ours.put(i, Payloads.plain(i, 2, 1000 + i * 300));
		// The process dies before any commit: every entry still holds version 1, complete.
		ours.abandon();
		Map<Integer, Integer> found = readWithVanilla(region());
		assertEquals(50, found.size());
		for (int v : found.values()) assertEquals(1, v);

		// After one commit step the header points at the new records (forced first), and the old sectors are still
		// reserved until the next step: dying here gives version 2 everywhere.
		Ours again = new Ours(region());
		for (int i = 0; i < 50; i++) again.put(i, Payloads.plain(i, 2, 1000 + i * 300));
		assertTrue(again.w.commitOnce());
		again.abandon();
		found = readWithVanilla(region());
		for (int v : found.values()) assertEquals(2, v);
	}

	@Test
	void oldSectorsAreNotReusedBeforeTheHeaderIsDurable() throws IOException {
		Committer.paused = true;
		Ours ours = new Ours(region());
		ours.put(0, Payloads.plain(0, 1, 9000));
		ours.w.commitOnce();
		ours.w.commitOnce();
		int first = ours.info[0];
		// Rewrite and commit one step: header now points at the new record, the old one awaits the next force.
		ours.put(0, Payloads.plain(0, 2, 9000));
		ours.w.commitOnce();
		// Another chunk of the same size must not land in chunk 0's old sectors yet.
		ours.put(1, Payloads.plain(1, 1, 9000));
		assertFalse(overlaps(first, ours.info[1]), "reused sectors still referenced by the durable header");
		ours.close();
	}

	private static boolean overlaps(int a, int b) {
		int as = a >>> 8, ae = as + (a & 0xFF), bs = b >>> 8, be = bs + (b & 0xFF);
		return as < be && bs < ae;
	}

	@Test
	void damagedRecordsAreReportedAndNeverOverwritten() throws IOException {
		try (Ours ours = new Ours(region())) {
			for (int i = 0; i < 10; i++) ours.put(i, Payloads.plain(i, 1, 6000));
		}
		int[] header = new int[1024];
		try (RandomAccessFile raf = new RandomAccessFile(region(), "rw")) {
			raf.seek(0);
			for (int i = 0; i < 1024; i++) header[i] = raf.readInt();
			int fileSectors = (int) (raf.length() / 4096);
			setHeader(raf, 1, (fileSectors + 50) << 8 | 2); // past the end
			raf.seek((long) (header[2] >>> 8) * 4096);
			raf.writeInt(0x7fffffff); // impossible length
			raf.seek((long) (header[3] >>> 8) * 4096 + 4);
			raf.writeByte(7); // unknown compression
			raf.seek((long) (header[5] >>> 8) * 4096 + 5);
			raf.write(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}); // damaged compressed data
			setHeader(raf, 6, 5_000_000 << 8 | 3); // wild
			for (int i = 0; i < 9; i++) assertTrue((header[i] >>> 8) < (header[9] >>> 8), "entry 9 is the last record");
			raf.setLength((long) (header[9] >>> 8) * 4096 + 100); // entry 9 truncated mid-record
			raf.seek(0);
			for (int i = 0; i < 1024; i++) header[i] = raf.readInt();
		}
		byte[] before = Files.readAllBytes(region().toPath());

		try (Ours ours = new Ours(region())) {
			assertEquals(RegionWrites.Raw.Status.BAD_POINTER, ours.w.read(1).status);
			assertEquals(RegionWrites.Raw.Status.BAD_LENGTH, ours.w.read(2).status);
			assertEquals(RegionWrites.Raw.Status.BAD_VERSION, ours.w.read(3).status);
			RegionWrites.Raw damaged = ours.w.read(5);
			assertEquals(RegionWrites.Raw.Status.OK, damaged.status);
			assertThrows(IOException.class, () -> Payloads.checkCompressed(damaged.bytes));
			assertEquals(RegionWrites.Raw.Status.BAD_POINTER, ours.w.read(6).status);
			// Opening padded the cut-off file to its next whole sector: the record is either still cut off, or reads in
			// full with zeros where its data was lost and fails where the chunk is decompressed.
			RegionWrites.Raw cut = ours.w.read(9);
			if (cut.status != RegionWrites.Raw.Status.TRUNCATED) {
				assertEquals(RegionWrites.Raw.Status.OK, cut.status);
				assertThrows(IOException.class, () -> Payloads.checkCompressed(cut.bytes));
			}
			assertEquals(RegionWrites.Raw.Status.OK, ours.w.read(0).status);
			assertEquals(RegionWrites.Raw.Status.ABSENT, ours.w.read(500).status);

			Random r = new Random(3);
			for (int n = 0; n < 400; n++) {
				int index = 100 + r.nextInt(300);
				ours.put(index, Payloads.plain(index, n, 100 + r.nextInt(30000)));
			}
		}
		byte[] after = Files.readAllBytes(region().toPath());
		assertTrue(after.length < 30 << 20, "a wild header entry ballooned the file to " + after.length);
		for (int i : new int[] {1, 2, 3, 5, 6}) assertEquals(header[i], readHeader(i), "header entry " + i + " changed");
		// Every byte of the records (damaged or not; none was saved again) that the file had is still there.
		for (int i = 0; i < 10; i++) {
			int start = header[i] >>> 8, count = header[i] & 0xFF;
			long from = (long) start * 4096, to = Math.min(before.length, (long) (start + count) * 4096);
			for (long p = from; p < to; p++) assertEquals(before[(int) p], after[(int) p], "damaged record " + i + " changed at " + p);
		}
	}

	private void setHeader(RandomAccessFile raf, int index, int value) throws IOException {
		raf.seek(index * 4L);
		raf.writeInt(value);
	}

	private int readHeader(int index) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(region(), "r")) {
			raf.seek(index * 4L);
			return raf.readInt();
		}
	}

	/** Vanilla's constructor, given a file that isn't a whole number of sectors, zeroes the start of the chunk index. */
	@Test
	void vanillasOpenerWipesTheIndexOfAnUnalignedFile() throws IOException {
		Map<Integer, Integer> written = unalignedRegion();
		RegionFile vanilla = new RegionFile(region());
		int present = 0;
		for (int i : written.keySet()) if (vanilla.hasChunkData(i & 31, i >> 5)) present++;
		vanilla.close();
		assertTrue(present < written.size(), "the control: vanilla's opener should lose entries here");
	}

	/** The fix the game runs before that constructor: the same file, padded at the end, opens with every entry. */
	@Test
	void alignedBeforeOpeningEveryEntryStays() throws IOException {
		Map<Integer, Integer> written = unalignedRegion();
		RegionWrites.alignBeforeOpen(region());
		assertEquals(written, readWithVanilla(region()));
		// And our writer carries on from there.
		try (Ours ours = new Ours(region())) {
			ours.put(500, Payloads.plain(500, 1, 9000));
		}
		written.put(500, 1);
		assertEquals(written, readWithVanilla(region()));
	}

	/** 40 chunks, then a few hundred stray bytes at the end, as a write interrupted while growing the file leaves it. */
	private Map<Integer, Integer> unalignedRegion() throws IOException {
		Map<Integer, Integer> written = new HashMap<>();
		try (Ours ours = new Ours(region())) {
			for (int i = 0; i < 40; i++) {
				ours.put(i, Payloads.plain(i, 1, 3000 + i * 50));
				written.put(i, 1);
			}
		}
		try (RandomAccessFile raf = new RandomAccessFile(region(), "rw")) {
			raf.seek(raf.length());
			raf.write(new byte[300]);
		}
		return written;
	}

	/**
	 * A header entry pointing far past the end isn't counted; replacing it later mustn't uncount sectors that by then
	 * hold other chunks' records.
	 */
	@Test
	void aWildEntryNeverFreesLiveSectors() throws IOException {
		Map<Integer, Integer> latest = new HashMap<>();
		try (Ours ours = new Ours(region())) {
			ours.put(0, Payloads.plain(0, 1, 2000));
			latest.put(0, 1);
		}
		try (RandomAccessFile raf = new RandomAccessFile(region(), "rw")) {
			int fileSectors = (int) (raf.length() / 4096);
			raf.seek(6 * 4L);
			raf.writeInt((fileSectors + RegionWrites.RESERVE_PAST_END + 100) << 8 | 3);
		}
		Random r = new Random(6);
		try (Ours ours = new Ours(region())) {
			// Grow the file well past the wild entry's sectors, with incompressible chunks.
			for (int n = 0; n < 600; n++) {
				int index = 10 + r.nextInt(300);
				ours.put(index, incompressible(index, n, 12000));
				latest.put(index, n);
			}
			// Now save chunk 6 normally: what its wild entry pointed at must not be released.
			ours.put(6, Payloads.plain(6, 1, 2000));
			latest.put(6, 1);
			for (int n = 600; n < 1200; n++) {
				int index = 10 + r.nextInt(300);
				ours.put(index, incompressible(index, n, 12000));
				latest.put(index, n);
			}
		}
		assertEquals(latest, readWithVanilla(region()));
	}

	private static byte[] incompressible(int index, int version, int size) {
		return Payloads.plainRandom(index, version, size);
	}

	@Test
	void oversizedChunksGoBesideTheRegion() throws IOException {
		try (Ours ours = new Ours(region())) {
			ours.put(7, Payloads.plain(7, 1, 3000));
		}
		// Incompressible and over 1 MB compressed: too big for the format.
		byte[] huge = new byte[1_200_000];
		new Random(4).nextBytes(huge);
		byte[] compressed = Payloads.deflate(huge);
		try (Ours ours = new Ours(region())) {
			assertEquals(-1, ours.w.write(7, compressed, compressed.length));
			ours.w.writeOversized(7, compressed, compressed.length);
			RegionWrites.Raw raw = ours.w.read(7);
			assertEquals(RegionWrites.Raw.Status.OVERSIZED, raw.status);
			assertArrayEquals(compressed, raw.bytes);
		}
		File side = Oversized.sideFile(region(), 7);
		assertTrue(side.exists());
		// Plain b1.7.3 reads the last version that fit.
		assertEquals(1, readWithVanilla(region()).get(7));
		try (Ours ours = new Ours(region())) {
			assertEquals(RegionWrites.Raw.Status.OVERSIZED, ours.w.read(7).status);
			// It shrinks again: a normal save supersedes the side file once committed.
			ours.put(7, Payloads.plain(7, 3, 2000));
			assertEquals(RegionWrites.Raw.Status.OK, ours.w.read(7).status);
		}
		assertFalse(side.exists(), "superseded side file left behind");
		assertEquals(3, readWithVanilla(region()).get(7));
	}

	@Test
	void aSideFileSupersededByPlainB173IsIgnored() throws IOException {
		try (Ours ours = new Ours(region())) {
			ours.put(9, Payloads.plain(9, 1, 3000));
		}
		byte[] big = new byte[1_200_000];
		new Random(5).nextBytes(big);
		byte[] compressed = Payloads.deflate(big);
		try (Ours ours = new Ours(region())) {
			ours.w.writeOversized(9, compressed, compressed.length);
		}
		// Plain b1.7.3 saves the chunk later (same sector count: in place, with a new timestamp).
		RegionFile vanilla = new RegionFile(region());
		DataOutputStream out = vanilla.getChunkOutputStream(9, 0);
		out.write(Payloads.plain(9, 2, 3000));
		out.close();
		vanilla.close();
		try (RandomAccessFile raf = new RandomAccessFile(region(), "rw")) {
			raf.seek(4096 + 9 * 4);
			raf.writeInt(12345); // make sure the timestamp differs even within the same second
		}
		try (Ours ours = new Ours(region())) {
			RegionWrites.Raw raw = ours.w.read(9);
			assertEquals(RegionWrites.Raw.Status.OK, raw.status);
			assertEquals(2, Payloads.checkCompressed(raw.bytes)[1]);
		}
		assertFalse(Oversized.sideFile(region(), 9).exists());
	}

	@Test
	void nbtCheckRejectsWhatWouldCrashTheParser() throws IOException {
		java.io.ByteArrayOutputStream ok = new java.io.ByteArrayOutputStream();
		net.minecraft.nbt.NbtCompound root = new net.minecraft.nbt.NbtCompound();
		net.minecraft.nbt.NbtCompound level = new net.minecraft.nbt.NbtCompound();
		level.putByteArray("Blocks", new byte[32768]);
		level.putInt("xPos", 3);
		level.put("Entities", new net.minecraft.nbt.NbtList());
		root.put("Level", level);
		net.minecraft.nbt.NbtIo.write(root, new DataOutputStream(ok));
		byte[] good = ok.toByteArray();
		assertNull(NbtCheck.check(good));
		// A byte array claiming two billion bytes.
		byte[] bomb = good.clone();
		int at = indexOf(bomb, "Blocks".getBytes()) + 6;
		bomb[at] = 0x7f;
		assertNotNull(NbtCheck.check(bomb));
		// Cut short.
		assertNotNull(NbtCheck.check(java.util.Arrays.copyOf(good, good.length - 10)));
		// An unknown tag type.
		byte[] unknown = good.clone();
		unknown[indexOf(unknown, "xPos".getBytes()) - 3] = 11;
		assertNotNull(NbtCheck.check(unknown));
	}

	private static int indexOf(byte[] hay, byte[] needle) {
		outer:
		for (int i = 0; i + needle.length <= hay.length; i++) {
			for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
			return i;
		}
		throw new AssertionError("not found");
	}
}
