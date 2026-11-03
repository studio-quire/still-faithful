package org.studioquire.oldfaithful.saves;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Crash-safe writes for one McRegion file, in vanilla's format and layout rules, so plain b1.7.3 and other tools read
 * the result unchanged.
 *
 * <p>Vanilla rewrites a chunk in place when it still fits its sectors, and otherwise frees the old sectors before
 * choosing new ones, so a crash mid-write leaves a torn record the header points at. Here a chunk is always written to
 * sectors nobody references, and the header on disk is only changed to point at it once that data has been forced to
 * disk; the sectors it pointed at before are only reused once that header change has itself been forced. So at every
 * moment, through a process crash or a power cut, each header entry on disk points at a complete record: the new one
 * or the one before it. The in-memory header (vanilla's {@code chunkBlockInfo}, which the game and other mods read)
 * moves at once, so reads in the same session see the new data.
 *
 * <p>The forcing and the header writes happen in {@link #commitOnce}, run by the {@link Committer}'s thread shortly
 * after writes (so the game thread never waits on the disk), and synchronously on close and on a full save.
 *
 * <p>Vanilla's fields are shared, not copied: {@code mem}, {@code memTimes} and {@code free} are the RegionFile's own
 * {@code chunkBlockInfo}, {@code chunkSaveTimes} and {@code blockEmptyFlags} (true = free), kept current. All state is
 * guarded by {@code owner}'s monitor, the same one vanilla's synchronized methods use.
 */
public final class RegionWrites {
	static final int SECTOR = 4096;
	/** More sectors than any real region needs (1024 chunks of at most 255 sectors), so wild header values are ignored. */
	static final int MAX_SECTORS = 1024 * 256 + 2;
	/** How far past the end of a damaged file a header entry's claim is still honoured (4 MB). */
	static final int RESERVE_PAST_END = 1024;
	/** Vanilla's sector count for a record of {@code size} compressed bytes (it over-counts by one at exact fits). */
	static int sectorsFor(int size) {
		return (size + 5) / SECTOR + 1;
	}

	final Object owner;
	final File file;
	private final RandomAccessFile raf;
	private final int[] mem;
	private final int[] memTimes;
	private final ArrayList<Boolean> free;
	private final int[] disk = new int[1024];
	private final int[] diskTimes = new int[1024];
	private final long[] writeSeq = new long[1024];
	/** Side files of oversized chunks to delete once the header entry superseding them is durable. */
	private final File[] sideToDelete = new File[1024];
	private int[] refs;
	/**
	 * The entry whose sectors are counted in {@code refs} for each index's in-memory and on-disk header (0 if none):
	 * an entry claiming sectors the file doesn't have isn't counted, and releasing it later mustn't uncount sectors
	 * that by then belong to someone else.
	 */
	private final int[] memRef = new int[1024], diskRef = new int[1024];
	private int sectors;
	private long seq;
	/** Old header targets (entries) and superseded side files waiting for the next force before they're released. */
	private final List<Integer> releaseAfterForce = new ArrayList<>();
	private final List<File> deleteAfterForce = new ArrayList<>();
	private final ReentrantLock commitLock = new ReentrantLock();
	private boolean closed;
	/** Whether any oversized-chunk side file may exist for this region (checked once, so writes don't stat files). */
	private boolean mayHaveSideFiles;
	/** Counts writes, so the committer can tell whether one arrived while it was checking (no lock needed to read). */
	volatile long writes;

	/** Wraps a region file vanilla has just opened (its constructor read the header into these arrays). */
	public RegionWrites(Object owner, File file, RandomAccessFile raf, int[] chunkBlockInfo, int[] chunkSaveTimes,
			ArrayList<Boolean> blockEmptyFlags) {
		this.owner = owner;
		this.file = file;
		this.raf = raf;
		this.mem = chunkBlockInfo;
		this.memTimes = chunkSaveTimes;
		this.free = blockEmptyFlags;
		// Every sector the file has, a partial last one included (left by a crash mid-grow), and the sectors a header
		// entry claims just past the end (a damaged file): none of those is handed out while referenced, so such an
		// entry can't come to alias another chunk's record as the file grows. Entries pointing far beyond the end are
		// garbage with nothing behind them, and aren't allowed to balloon the file.
		long length;
		try {
			length = raf.length();
		} catch (IOException e) {
			length = (long) blockEmptyFlags.size() * SECTOR;
		}
		int fileSectors = (int) Math.min(MAX_SECTORS, (length + SECTOR - 1) / SECTOR);
		int count = fileSectors;
		for (int i = 0; i < 1024; i++) {
			int start = chunkBlockInfo[i] >>> 8, n = chunkBlockInfo[i] & 0xFF;
			if (start >= 2 && n > 0 && start + n <= fileSectors + RESERVE_PAST_END) count = Math.max(count, start + n);
		}
		this.sectors = Math.max(2, Math.max(count, blockEmptyFlags.size()));
		while (free.size() < sectors) free.add(Boolean.TRUE);
		this.refs = new int[Math.max(sectors, 64)];
		System.arraycopy(mem, 0, disk, 0, 1024);
		System.arraycopy(memTimes, 0, diskTimes, 0, 1024);
		for (int i = 0; i < 1024; i++) {
			memRef[i] = addRef(mem[i]);
			diskRef[i] = addRef(disk[i]);
		}
		for (int s = 0; s < sectors; s++) free.set(s, s >= 2 && refs[s] == 0);
		this.mayHaveSideFiles = Oversized.anyFor(file);
	}

	// --- sector accounting -------------------------------------------------------------------------------------------

	/** Counts an entry's sectors if they all lie within the file's sectors; returns what was counted (or 0). */
	private int addRef(int entry) {
		int start = entry >>> 8, count = entry & 0xFF;
		if (entry == 0 || start < 2 || count == 0 || start + count > sectors) return 0;
		adjust(start, count, 1);
		return entry;
	}

	/** Uncounts what {@link #addRef} counted. */
	private void removeRef(int counted) {
		if (counted != 0) adjust(counted >>> 8, counted & 0xFF, -1);
	}

	private void adjust(int start, int count, int delta) {
		for (int s = start; s < start + count; s++) {
			refs[s] += delta;
			free.set(s, refs[s] == 0);
		}
	}

	private int allocate(int count) {
		int run = 0;
		for (int s = 2; s < sectors; s++) {
			if (refs[s] == 0) {
				if (++run == count) return s - count + 1;
			} else {
				run = 0;
			}
		}
		return -1;
	}

	private void grow(int newSectors) {
		if (newSectors > refs.length) {
			int[] bigger = new int[Math.max(newSectors, refs.length * 2)];
			System.arraycopy(refs, 0, bigger, 0, refs.length);
			refs = bigger;
		}
		while (free.size() < newSectors) free.add(Boolean.TRUE);
		sectors = newSectors;
	}

	// --- writes ------------------------------------------------------------------------------------------------------

	/**
	 * Writes a chunk's compressed record ({@code data[0, size)}, zlib, vanilla's version 2) for local index
	 * {@code index} (x + z * 32). Returns the bytes the file grew by (vanilla's {@code bytesWritten} counts those), or
	 * -1 if the record is too big for the format (256 sectors or more), which the caller handles.
	 */
	public int write(int index, byte[] data, int size) throws IOException {
		int count = sectorsFor(size);
		if (count >= 256) return -1;
		int grown = 0;
		synchronized (owner) {
			if (closed) throw new IOException("region file closed: " + file);
			int start = allocate(count);
			byte[] record = new byte[5 + size];
			record[0] = (byte) ((size + 1) >>> 24);
			record[1] = (byte) ((size + 1) >>> 16);
			record[2] = (byte) ((size + 1) >>> 8);
			record[3] = (byte) (size + 1);
			record[4] = 2;
			System.arraycopy(data, 0, record, 5, size);
			if (start < 0) {
				start = sectors;
				grow(sectors + count);
			}
			long before = raf.length();
			long end = (long) (start + count) * SECTOR;
			if (end > before) {
				// Extending the file: the record and the zeros to the end of its last sector in one write, so the file is
				// never left a partial sector long (vanilla only reads records within whole sectors).
				byte[] padded = new byte[(int) (end - (long) start * SECTOR)];
				System.arraycopy(record, 0, padded, 0, record.length);
				record = padded;
			}
			raf.seek((long) start * SECTOR);
			raf.write(record);
			grown = (int) Math.max(0, raf.length() - before);
			int entry = start << 8 | count;
			int counted = addRef(entry);
			removeRef(memRef[index]);
			mem[index] = entry;
			memRef[index] = counted;
			memTimes[index] = (int) (System.currentTimeMillis() / 1000L);
			writeSeq[index] = ++seq;
			writes++;
			if (mayHaveSideFiles) {
				File side = Oversized.sideFile(file, index);
				if (side.exists()) sideToDelete[index] = side;
			}
		}
		Committer.dirty(this);
		return grown;
	}

	/**
	 * A chunk too big for the region format (1 MB compressed or more; vanilla drops it without a word). Its record goes
	 * into a side file beside the region, stamped with the header entry it supersedes; the region keeps its previous
	 * record, which is what plain b1.7.3 will read. A later normal save supersedes the side file.
	 */
	public void writeOversized(int index, byte[] data, int size) throws IOException {
		int stampEntry, stampTime;
		synchronized (owner) {
			// A normal save of this chunk not yet committed is superseded too: the header must stay as the stamp says.
			if (mem[index] != disk[index]) {
				int counted = addRef(disk[index]);
				removeRef(memRef[index]);
				mem[index] = disk[index];
				memRef[index] = counted;
				memTimes[index] = diskTimes[index];
				writeSeq[index] = 0;
			}
			sideToDelete[index] = null;
			mayHaveSideFiles = true;
			writes++;
			stampEntry = disk[index];
			stampTime = diskTimes[index];
		}
		Oversized.write(Oversized.sideFile(file, index), stampEntry, stampTime, data, size);
	}

	// --- reads -------------------------------------------------------------------------------------------------------

	/** What the careful chunk load finds for a local index. */
	public static final class Raw {
		public enum Status { ABSENT, OK, BAD_POINTER, BAD_LENGTH, BAD_VERSION, TRUNCATED, IO_ERROR, OVERSIZED }

		public final Status status;
		/** OK and OVERSIZED: the compressed payload; otherwise whatever bytes of the record could be read, for a backup. */
		public final byte[] bytes;
		/** OK and OVERSIZED: 1 gzip, 2 zlib. */
		public final int version;
		public final String detail;

		Raw(Status status, byte[] bytes, int version, String detail) {
			this.status = status;
			this.bytes = bytes;
			this.version = version;
			this.detail = detail;
		}
	}

	public Raw read(int index) {
		synchronized (owner) {
			try {
				File side = Oversized.sideFile(file, index);
				if (mayHaveSideFiles && side.exists()) {
					if (mem[index] == disk[index]) {
						Oversized.Record r = Oversized.read(side);
						if (r != null && r.stampEntry == disk[index] && r.stampTime == diskTimes[index]) {
							return new Raw(Raw.Status.OVERSIZED, r.data, r.version, side.getName());
						}
						// Superseded (by a save here, or by plain b1.7.3 since): the region's record is newer.
						if (r != null) Log.info("Removing a superseded oversized-chunk file " + side);
						if (!side.delete()) Log.warn("Could not delete " + side);
					}
				}
				int entry = mem[index];
				if (entry == 0) return new Raw(Raw.Status.ABSENT, null, 0, null);
				int start = entry >>> 8, count = entry & 0xFF;
				long fileLength = raf.length();
				if (start < 2 || count == 0 || (long) start * SECTOR + 5 > fileLength) {
					return new Raw(Raw.Status.BAD_POINTER, readSpan(start, count, fileLength), 0,
							"header points at sectors " + start + "+" + count + ", file has " + (fileLength + SECTOR - 1) / SECTOR);
				}
				raf.seek((long) start * SECTOR);
				int length = raf.readInt();
				if (length <= 1 || length > count * SECTOR - 4) {
					return new Raw(Raw.Status.BAD_LENGTH, readSpan(start, count, fileLength), 0,
							"record length " + length + " for " + count + " sectors");
				}
				int version = raf.readByte();
				if (version != 1 && version != 2) {
					return new Raw(Raw.Status.BAD_VERSION, readSpan(start, count, fileLength), 0, "compression type " + version);
				}
				byte[] payload = new byte[length - 1];
				if ((long) start * SECTOR + 5 + payload.length > fileLength) {
					return new Raw(Raw.Status.TRUNCATED, readSpan(start, count, fileLength), version,
							"record runs past the end of the file");
				}
				raf.readFully(payload);
				return new Raw(Raw.Status.OK, payload, version, null);
			} catch (IOException e) {
				return new Raw(Raw.Status.IO_ERROR, null, 0, String.valueOf(e));
			}
		}
	}

	/** The raw bytes of a span of sectors, as far as the file has them (for backups). */
	private byte[] readSpan(int start, int count, long fileLength) throws IOException {
		if (start < 2 || count == 0) return new byte[0];
		long from = (long) start * SECTOR;
		long to = Math.min(fileLength, (long) (start + count) * SECTOR);
		if (to <= from) return new byte[0];
		byte[] out = new byte[(int) (to - from)];
		raf.seek(from);
		raf.readFully(out);
		return out;
	}

	/** The header entry and time on disk for an index (the oversized stamp). */
	int diskEntry(int index) {
		synchronized (owner) {
			return disk[index];
		}
	}

	// --- commits -----------------------------------------------------------------------------------------------------

	/** True if something here still has to reach the disk. */
	boolean pending() {
		synchronized (owner) {
			if (closed) return false;
			if (!releaseAfterForce.isEmpty() || !deleteAfterForce.isEmpty()) return true;
			for (int i = 0; i < 1024; i++) if (mem[i] != disk[i]) return true;
			return false;
		}
	}

	/**
	 * One commit step: force everything written so far, then release what the previous step's header writes made
	 * reusable, then point the header at every record the force covered. Returns true if another step is needed (the
	 * header writes just made aren't forced yet, or writes arrived meanwhile).
	 */
	boolean commitOnce() throws IOException {
		commitLock.lock();
		try {
			long covered;
			List<Integer> release;
			List<File> delete;
			synchronized (owner) {
				if (closed) return false;
				covered = seq;
				release = new ArrayList<>(releaseAfterForce);
				releaseAfterForce.clear();
				delete = new ArrayList<>(deleteAfterForce);
				deleteAfterForce.clear();
			}
			raf.getChannel().force(true);
			boolean more = false;
			synchronized (owner) {
				for (int entry : release) removeRef(entry);
				byte[] four = new byte[4];
				for (int i = 0; i < 1024; i++) {
					if (mem[i] == disk[i]) continue;
					if (writeSeq[i] > covered) {
						more = true;
						continue;
					}
					writeInt(i * 4, mem[i], four);
					writeInt(SECTOR + i * 4, memTimes[i], four);
					releaseAfterForce.add(diskRef[i]);
					diskRef[i] = addRef(mem[i]);
					disk[i] = mem[i];
					diskTimes[i] = memTimes[i];
					if (sideToDelete[i] != null) {
						deleteAfterForce.add(sideToDelete[i]);
						sideToDelete[i] = null;
					}
				}
				if (!releaseAfterForce.isEmpty() || !deleteAfterForce.isEmpty()) more = true;
			}
			for (File f : delete) {
				if (f.exists() && !f.delete()) Log.warn("Could not delete " + f);
			}
			return more;
		} finally {
			commitLock.unlock();
		}
	}

	/** A single write call, so a header entry is never half-written. */
	private void writeInt(long position, int value, byte[] four) throws IOException {
		four[0] = (byte) (value >>> 24);
		four[1] = (byte) (value >>> 16);
		four[2] = (byte) (value >>> 8);
		four[3] = (byte) value;
		raf.seek(position);
		raf.write(four);
	}

	/** Commits everything and stops accepting writes; the caller then closes the file. */
	public void commitAndClose() {
		try {
			// A region with nothing pending closes without forcing (RegionIo can close 256 at once).
			for (int i = 0; i < 16 && pending() && commitOnce(); i++) {
			}
		} catch (IOException e) {
			Log.warn("Could not finish saving " + file + ": " + e);
		}
		synchronized (owner) {
			closed = true;
		}
		Committer.forget(this);
	}

	/**
	 * Run before vanilla's RegionFile constructor opens a file. That constructor pads a file whose length isn't a
	 * whole number of sectors by writing zeros at the file pointer, which is at the start of the file: the zeros land
	 * on the chunk index, so those chunks read as never generated, get generated anew and saved over the real ones.
	 * Padded here at the end instead, the constructor finds nothing to pad. A file shorter than the two header
	 * sectors is padded to them, so the constructor reads a whole header.
	 */
	public static void alignBeforeOpen(File file) {
		long length = file.length();
		if (length == 0 || length >= 2 * SECTOR && length % SECTOR == 0) return;
		long target = Math.max(2L * SECTOR, (length + SECTOR - 1) / SECTOR * SECTOR);
		try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
			raf.seek(length);
			raf.write(new byte[(int) (target - length)]);
			Log.warn(file + " was " + length + " bytes, not a whole number of sectors (a save interrupted by a crash, or"
					+ " another tool); padded to " + target + " at the end, leaving its chunk index as it was");
		} catch (IOException e) {
			Log.warn("Could not pad " + file + ": " + e);
		}
	}

	// --- for tests ---------------------------------------------------------------------------------------------------

	/** Opens a region file as vanilla's constructor does (header, sector flags), for tests without the game. */
	static RegionWrites openForTest(File file, int[] chunkBlockInfo, int[] chunkSaveTimes, ArrayList<Boolean> flags,
			RandomAccessFile raf) throws IOException {
		if (raf.length() % SECTOR != 0) throw new IOException("unaligned: open through alignBeforeOpen, as the game does");
		if (raf.length() < 2 * SECTOR) {
			raf.seek(raf.length());
			raf.write(new byte[(int) (2 * SECTOR - raf.length())]);
		}
		int whole = (int) (raf.length() / SECTOR);
		for (int s = 0; s < whole; s++) flags.add(Boolean.TRUE);
		flags.set(0, false);
		flags.set(1, false);
		raf.seek(0);
		for (int i = 0; i < 1024; i++) chunkBlockInfo[i] = raf.readInt();
		for (int i = 0; i < 1024; i++) chunkSaveTimes[i] = raf.readInt();
		return new RegionWrites(new Object(), file, raf, chunkBlockInfo, chunkSaveTimes, flags);
	}

	/** Reads a whole small file (helpers for tests and backups). */
	static byte[] readAll(File f) throws IOException {
		try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
			byte[] b = new byte[(int) f.length()];
			in.readFully(b);
			return b;
		}
	}
}
