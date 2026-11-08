package org.studioquire.oldfaithful.saves;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

/** level.dat and player files: the fallbacks and copies the mixins use when loading goes wrong. */
public final class WorldFiles {
	private WorldFiles() {
	}

	/**
	 * A save interrupted between its two renames leaves no level.dat but a complete, forced level.dat_new (newer than
	 * level.dat_old, which vanilla would fall back to). Returns its Data, or null if that isn't the situation.
	 */
	public static NbtCompound interruptedLevel(File dir) {
		File current = new File(dir, "level.dat");
		File fresh = new File(dir, "level.dat_new");
		if (current.exists() || !fresh.exists()) return null;
		try (InputStream in = new FileInputStream(fresh)) {
			NbtCompound data = NbtIo.readCompressed(in).getCompound("Data");
			Log.warn("level.dat was missing (a save was interrupted between its renames); using the complete level.dat_new in " + dir);
			return data;
		} catch (Exception e) {
			return null;
		}
	}

	private static final Set<String> protectedFiles = Collections.synchronizedSet(new HashSet<>());

	/**
	 * Neither level.dat nor level.dat_old could be read: copies of both are kept before the game writes new ones. If a
	 * copy can't be made (a full disk, a file another program holds), level.dat isn't saved this session at all.
	 */
	public static void levelUnreadable(File dir) {
		File current = new File(dir, "level.dat");
		File old = new File(dir, "level.dat_old");
		if (!current.exists() && !old.exists()) return;
		boolean allKept = true;
		File kept = null;
		for (File f : new File[] {current, old}) {
			if (!f.exists()) continue;
			File k = Backups.file(dir, f, "couldn't be read");
			if (k == null) allKept = false;
			else if (kept == null) kept = k;
		}
		if (!allKept) {
			protectedFiles.add(current.getAbsolutePath());
			Notices.say("This world's level.dat couldn't be read, and couldn't be copied aside (see the log). The game runs it with"
					+ " new world settings for now, but won't save level.dat, so the original stays as it is.",
					"This world's level.dat couldn't be read, and couldn't be copied aside (see above). The game runs it with"
					+ " new world settings for now, but won't save level.dat, so the original stays as it is.");
			return;
		}
		Notices.say("This world's level.dat couldn't be read, so the game starts it with new world settings (seed, spawn, time"
				+ " and, in single player, your inventory). The originals are kept in " + Notices.shortPath(kept.getParentFile())
				+ "; the terrain already saved is untouched.");
	}

	/**
	 * A player's file exists but couldn't be read: kept before the game saves a fresh player over it. If the copy
	 * can't be made (often the same cause: the file is locked or the disk full), that player's file isn't saved over
	 * this session.
	 */
	public static void playerUnreadable(File worldDir, File file, String name) {
		if (!file.exists()) return;
		File kept = Backups.file(worldDir, file, "couldn't be read when " + name + " joined");
		if (kept == null) {
			protectedFiles.add(file.getAbsolutePath());
			Log.warn("Player data for " + name + " couldn't be read or copied aside; they start fresh, and their file won't be saved"
					+ " over this session, so the original stays as it is");
			return;
		}
		Log.warn("Player data for " + name + " couldn't be read; they start fresh. The original is kept at " + kept);
	}

	/** A file whose unreadable original couldn't be copied aside is never saved over this session. */
	public static boolean isProtected(File file) {
		return !protectedFiles.isEmpty() && protectedFiles.contains(file.getAbsolutePath());
	}
}
