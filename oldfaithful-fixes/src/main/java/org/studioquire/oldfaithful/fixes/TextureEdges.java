package org.studioquire.oldfaithful.fixes;

/**
 * #72 (docs/bugs.md): a block face's texture coordinates start exactly on its tile's edge in the terrain atlas, so a
 * pixel just inside that edge can be interpolated a hair outside it and read the tile next door; where that texel is
 * transparent (the first row's tiles wrap to the last row's cracking stages and empty tiles), the alpha test drops the
 * pixel and the sky shows through.
 *
 * <p>While a chunk is built, each finished quad's texture coordinates are looked at as a whole: a coordinate lying
 * exactly on a tile edge (a multiple of 1/16) at the low end of the quad's range moves in by {@link #INSET}, and one at
 * the high end moves in the same way (some of beta's rotated faces end exactly on the edge too). Quads whose range
 * crosses a tile edge are left alone. Beta's own 0.01-texel inset at a face's far end is not on an edge and stays.
 */
public final class TextureEdges {
	/** A thousandth of a texel of the 256-texel atlas: far beyond the interpolation's error, far below what shows. */
	public static final float INSET = 1f / 1000f / 256f;

	/** Set while a chunk is being built. */
	public static boolean building;

	private TextureEdges() {
	}

	/**
	 * The quad whose vertices are the {@code count} entries (8 ints each) ending at {@code end} in {@code buffer}: its u
	 * (offset 3) and v (offset 4) pulled in from tile edges.
	 */
	public static void inset(int[] buffer, int end, int count) {
		int start = end - count * 8;
		if (start < 0) return;
		inset(buffer, start, end, 3);
		inset(buffer, start, end, 4);
	}

	private static void inset(int[] buffer, int start, int end, int offset) {
		float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
		for (int i = start + offset; i < end; i += 8) {
			float c = Float.intBitsToFloat(buffer[i]);
			min = Math.min(min, c);
			max = Math.max(max, c);
		}
		if (!(max > min) || Math.floor(min * 16) != Math.floor(Math.nextDown(max) * 16)) return;
		boolean minOnEdge = onEdge(min), maxOnEdge = onEdge(max);
		if (!minOnEdge && !maxOnEdge) return;
		for (int i = start + offset; i < end; i += 8) {
			float c = Float.intBitsToFloat(buffer[i]);
			if (c == min && minOnEdge) buffer[i] = Float.floatToRawIntBits(c + INSET);
			else if (c == max && maxOnEdge) buffer[i] = Float.floatToRawIntBits(c - INSET);
		}
	}

	private static boolean onEdge(float c) {
		float t = c * 16;
		return t == (float) Math.rint(t);
	}
}
