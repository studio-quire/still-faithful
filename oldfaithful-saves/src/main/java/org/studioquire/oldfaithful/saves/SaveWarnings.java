package org.studioquire.oldfaithful.saves;

import java.io.File;

/** Warnings from the mixins about saves that went differently from usual. */
public final class SaveWarnings {
	private SaveWarnings() {
	}

	public static void oversized(File region, int localX, int localZ, int size) {
		String logged = "A chunk in " + region.getName() + " (entry " + localX + ", " + localZ + ") is too big for the region format ("
				+ size / 1024 + " KB compressed; plain b1.7.3 silently stops saving it). It's saved beside the region, in "
				+ Oversized.DIR + "/; plain b1.7.3 will see its last version that fit.";
		String said = "holds too much to fit in beta's save format (often a great many dropped items), so it's saved in a file of"
				+ " its own, in a " + Oversized.DIR + " folder beside the world's region files. Plain beta would load it as it"
				+ " was when it last fit.";
		int[] at = regionAt(region);
		if (at == null) Notices.say("A chunk " + said, logged);
		else Notices.say("The chunk at blocks " + (at[0] * 32 + localX) * 16 + ", " + (at[1] * 32 + localZ) * 16 + " " + said, logged);
	}

	/** r.X.Z.mcr's X and Z, or null for a name not like that. */
	private static int[] regionAt(File region) {
		String[] parts = region.getName().split("\\.");
		if (parts.length != 4 || !parts[0].equals("r")) return null;
		try {
			return new int[] {Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static void writeFailed(File region, int localX, int localZ, Exception e) {
		Log.warn("Could not save a chunk to " + region + " (entry " + localX + ", " + localZ + "): " + e
				+ "; its previous version stays on disk");
	}
}
