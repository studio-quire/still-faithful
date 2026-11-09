package org.studioquire.oldfaithful.polish;

import java.awt.image.BufferedImage;

/**
 * render.chestLids: beta draws each half of a double chest with the single chest's lid (terrain.png's tile 25), so a
 * pair's top and bottom show a border down the middle; later versions draw one lid. The halves of one are made here
 * from whatever lid the texture pack has, each from the tile's outer half and its middle, so the border stays round
 * the outside and the middle's grain joins without a dark line, and put in four tiles beta leaves as placeholders and
 * never draws ({@link Chests#LIDS}: 250 to 253, the west, east, north and south halves). A chest's top and bottom both
 * lay the tile out by world x and z, so one set serves both.
 */
public final class ChestLids {
	private static final int LID = 25;

	private ChestLids() {
	}

	/** terrain.png with the joined lid's halves in it; the image as it was if it isn't a 16 by 16 sheet of square tiles. */
	public static BufferedImage withLids(BufferedImage terrain) {
		if (terrain == null) return null;
		int n = terrain.getWidth() / 16;
		if (n < 4 || terrain.getHeight() != terrain.getWidth()) return terrain;
		BufferedImage out = new BufferedImage(terrain.getWidth(), terrain.getHeight(), BufferedImage.TYPE_INT_ARGB);
		out.getGraphics().drawImage(terrain, 0, 0, null);
		int[] lid = out.getRGB(LID % 16 * n, LID / 16 * n, n, n, null, 0, n);
		int half = n / 2, quarter = n / 4;
		for (int slot = 0; slot < 4; slot++) {
			int[] tile = new int[n * n];
			for (int v = 0; v < n; v++) {
				for (int u = 0; u < n; u++) {
					int su = u, sv = v;
					// West and north keep the tile's first half and go on with its middle; east and south start with the middle.
					if (slot == 0) su = u < half ? u : u - quarter;
					else if (slot == 1) su = u < half ? u + quarter : u;
					else if (slot == 2) sv = v < half ? v : v - quarter;
					else sv = v < half ? v + quarter : v;
					tile[v * n + u] = lid[sv * n + su];
				}
			}
			int at = Chests.LIDS + slot;
			out.setRGB(at % 16 * n, at / 16 * n, n, n, tile, 0, n);
		}
		return out;
	}
}
