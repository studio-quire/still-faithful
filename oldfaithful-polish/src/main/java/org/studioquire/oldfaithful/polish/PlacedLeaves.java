package org.studioquire.oldfaithful.polish;

/**
 * #24: leaves a player placed. A leaves block's data is its kind in bits 1 and 2 (oak, spruce, birch) and beta's
 * "check for decay" mark in bit 8; bit 4 is unused in beta (every reader of leaf data masks with 3 or tests bits 1, 2
 * and 8), so it marks placed leaves, as later versions mark them in the same bit. The mark lives in the block's data,
 * so it's saved with the chunk, sent to clients with the block, and kept by a piston moving it. A vanilla game reading
 * such a world sees ordinary leaves (which decay once a log or leaves beside them is broken and no log is near).
 */
public final class PlacedLeaves {
	public static final int PLACED = 4;
	private static final int KIND = 3;

	private PlacedLeaves() {
	}

	/** The data a placed leaves block gets: its kind and the kept mark, not beta's decay check. */
	public static int placed(int meta) {
		return (meta & KIND) | PLACED;
	}

	public static boolean isPlaced(int meta) {
		return (meta & PLACED) != 0;
	}
}
