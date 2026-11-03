package org.studioquire.oldfaithful.saves;

import java.io.File;

/** Warnings from the mixins about saves that went differently from usual. */
public final class SaveWarnings {
	private SaveWarnings() {
	}

	public static void oversized(File region, int localX, int localZ, int size) {
		Notices.say("A chunk in " + region.getName() + " (entry " + localX + ", " + localZ + ") is too big for the region format ("
				+ size / 1024 + " KB compressed; plain b1.7.3 silently stops saving it). It's saved beside the region, in "
				+ Oversized.DIR + "/; plain b1.7.3 will see its last version that fit.");
	}

	public static void writeFailed(File region, int localX, int localZ, Exception e) {
		Log.warn("Could not save a chunk to " + region + " (entry " + localX + ", " + localZ + "): " + e
				+ "; its previous version stays on disk");
	}
}
