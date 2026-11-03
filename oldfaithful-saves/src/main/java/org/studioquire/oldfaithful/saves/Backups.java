package org.studioquire.oldfaithful.saves;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Copies of whatever couldn't be read, in a {@code corrupt/} folder in the world's folder (or its dimension's), written
 * and forced to disk before the game goes on: the bytes exactly as found, and a note saying what they are and why
 * they're there.
 */
final class Backups {
	private Backups() {
	}

	/** A chunk record (length, compression type, payload, as stored in the region). Returns the copy, or null. */
	static File chunk(File dimensionDir, int chunkX, int chunkZ, byte[] bytes, String why) {
		String name = "chunk." + chunkX + "." + chunkZ + "." + stamp();
		File copy = new File(new File(dimensionDir, "corrupt"), name + ".record");
		String note = "Chunk " + chunkX + ", " + chunkZ + " (blocks " + chunkX * 16 + ".." + (chunkX * 16 + 15) + ", "
				+ chunkZ * 16 + ".." + (chunkZ * 16 + 15) + ") of " + dimensionDir + "\n"
				+ "Region file: region/r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mcr, entry " + ((chunkX & 31) + (chunkZ & 31) * 32) + "\n"
				+ "Why: " + why + "\n"
				+ "The .record file holds the record's bytes exactly as they were found (a 4-byte length, a compression\n"
				+ "type byte, then the compressed data, as far as it could be read), for repair tools.\n"
				+ "Saved by Pure Faithful (purefaithful-saves).\n";
		return write(copy, bytes == null ? new byte[0] : bytes, note) ? copy : null;
	}

	/** A whole file (level.dat, a player's file) about to be replaced because it can't be read. */
	static File file(File worldDir, File damaged, String why) {
		File copy = new File(new File(worldDir, "corrupt"), damaged.getName() + "." + stamp());
		try {
			byte[] bytes = RegionWrites.readAll(damaged);
			String note = damaged + "\nWhy: " + why + "\nSaved by Pure Faithful (purefaithful-saves).\n";
			return write(copy, bytes, note) ? copy : null;
		} catch (IOException e) {
			Log.warn("Could not copy " + damaged + " aside: " + e);
			return null;
		}
	}

	private static boolean write(File copy, byte[] bytes, String note) {
		try {
			SafeFiles.replaceNow(copy, bytes);
			SafeFiles.replaceNow(new File(copy.getPath() + ".txt"), note.getBytes(StandardCharsets.UTF_8));
			Log.warn("Kept a copy: " + copy);
			return true;
		} catch (IOException e) {
			Log.warn("Could not keep a copy at " + copy + ": " + e);
			return false;
		}
	}

	private static String stamp() {
		return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
	}
}
