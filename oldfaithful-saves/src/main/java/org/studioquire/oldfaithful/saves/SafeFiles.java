package org.studioquire.oldfaithful.saves;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

/**
 * Whole-file saves that can't leave a file half-written or missing: the data goes to a temporary file of its own
 * beside the target, is forced to disk, and then replaces the target in one rename. The game thread only writes bytes
 * to the temporary file (or hands them over); forcing and renaming happen on the {@link Committer}'s thread.
 */
public final class SafeFiles {
	private static final AtomicLong SEQ = new AtomicLong();

	private SafeFiles() {
	}

	/** Writes and replaces now, on this thread. */
	static void replaceNow(File target, byte[] data) throws IOException {
		File parent = target.getAbsoluteFile().getParentFile();
		if (parent != null) parent.mkdirs();
		File tmp = tmpFor(target);
		try {
			writeForced(tmp, data);
			move(tmp, target);
		} finally {
			if (tmp.exists()) tmp.delete();
		}
	}

	/** Queues a replacement; a newer one for the same file supersedes it. */
	public static void replaceLater(File target, byte[] data) {
		Committer.enqueue(target, new Committer.Job() {
			@Override
			void run() throws IOException {
				replaceNow(target, data);
			}
		});
	}

	/**
	 * level.dat, the way vanilla rotates it (the previous one kept as level.dat_old), with the new file forced to disk
	 * before either rename. A crash between the renames leaves level.dat_new complete, which the load falls back to.
	 */
	public static void rotateLevelLater(File dir, byte[] data) {
		File current = new File(dir, "level.dat");
		Committer.enqueue(current, new Committer.Job() {
			@Override
			void run() throws IOException {
				File fresh = new File(dir, "level.dat_new");
				File old = new File(dir, "level.dat_old");
				writeForced(fresh, data);
				if (current.exists()) move(current, old);
				move(fresh, current);
			}
		});
	}

	/** Before the game reads a file: a save of it still queued (or running) is finished first. */
	public static void flush(File target) {
		Committer.flushFile(target);
	}

	/**
	 * An output stream for code that writes a whole NBT file through {@code new FileOutputStream(file)}: writes go to
	 * a temporary file of its own, and closing it queues the force and the rename over the target. A write that failed,
	 * or a file that doesn't hold complete NBT when closed (the game's NbtIo closes the stream in a finally, so a save
	 * that threw halfway still closes it), never replaces the target.
	 */
	public static FileOutputStream replacingStream(File target) throws IOException {
		return new ReplacingStream(target, tmpFor(target), false);
	}

	/** The same, but renamed over the target on close, on this thread (a menu action whose result is read at once). */
	public static FileOutputStream replacingStreamNow(File target) throws IOException {
		return new ReplacingStream(target, tmpFor(target), true);
	}

	private static final class ReplacingStream extends FileOutputStream {
		private final File target, tmp;
		private final boolean now;
		private boolean closed, failed;

		ReplacingStream(File target, File tmp, boolean now) throws IOException {
			super(tmp);
			this.target = target;
			this.tmp = tmp;
			this.now = now;
		}

		@Override
		public void write(int b) throws IOException {
			try {
				super.write(b);
			} catch (IOException e) {
				failed = true;
				throw e;
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			try {
				super.write(b, off, len);
			} catch (IOException e) {
				failed = true;
				throw e;
			}
		}

		@Override
		public void close() throws IOException {
			if (closed) return;
			closed = true;
			try {
				super.close();
			} catch (IOException e) {
				failed = true;
			}
			if (failed) {
				discardTmp(target, tmp, "a write to it failed");
				return;
			}
			if (now) {
				install(target, tmp);
				return;
			}
			Committer.enqueue(target, new Committer.Job() {
				@Override
				void run() throws IOException {
					install(target, tmp);
				}

				@Override
				void discard() {
					tmp.delete();
				}
			});
		}
	}

	/** Checks the temporary file holds complete NBT, forces it and renames it over the target. */
	private static void install(File target, File tmp) throws IOException {
		if (!tmp.exists()) return; // installed by an earlier attempt
		String bad = checkNbtFile(tmp);
		if (bad != null) {
			discardTmp(target, tmp, bad);
			return;
		}
		try (FileChannel ch = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE)) {
			ch.force(true);
		}
		move(tmp, target);
	}

	private static void discardTmp(File target, File tmp, String why) {
		Log.warn("Not replacing " + target + " with an incomplete save (" + why + "); the previous version stays");
		tmp.delete();
	}

	/** Null if the file is complete NBT (gzip-compressed, as map files are, or plain, as idcounts.dat is). */
	static String checkNbtFile(File f) {
		try {
			byte[] bytes = RegionWrites.readAll(f);
			if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1f && (bytes[1] & 0xFF) == 0x8b) {
				// GZIPInputStream checks the trailer's CRC and length, so a cut-off or damaged file fails here.
				try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
					bytes = out.toByteArray();
				}
			}
			return NbtCheck.check(bytes);
		} catch (IOException e) {
			return "unreadable: " + e;
		}
	}

	private static File tmpFor(File target) {
		return new File(target.getPath() + "." + SEQ.incrementAndGet() + ".oftmp");
	}

	private static void writeForced(File file, byte[] data) throws IOException {
		try (FileOutputStream out = new FileOutputStream(file)) {
			out.write(data);
			out.getChannel().force(true);
		}
	}

	static void move(File from, File to) throws IOException {
		try {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
