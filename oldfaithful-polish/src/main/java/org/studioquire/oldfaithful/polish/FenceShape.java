package org.studioquire.oldfaithful.polish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** #30: the post-and-arms shape itself, in a block's own coordinates ({@link Fences} puts it in the world). */
final class FenceShape {
	static final double LO = 0.375, HI = 0.625;
	static final double AIM_HEIGHT = 1.0, COLLISION_HEIGHT = 1.5;

	private FenceShape() {
	}

	/**
	 * The footprint as boxes, {minX, minZ, maxX, maxZ} each: one along z where it joins north or south, one along x
	 * where it joins east or west or doesn't join north or south (a lone post is the latter).
	 */
	static List<double[]> footprint(boolean west, boolean east, boolean north, boolean south) {
		List<double[]> boxes = new ArrayList<>(2);
		if (north || south) boxes.add(new double[] {LO, north ? 0 : LO, HI, south ? 1 : HI});
		if (west || east || !(north || south)) boxes.add(new double[] {west ? 0 : LO, LO, east ? 1 : HI, HI});
		return boxes;
	}

	/**
	 * A coordinate of the outline moved outward by {@code grow}. Every corner of the shape lies on 0, 3/8, 5/8 or 1 on
	 * each axis and moves away from the middle: a corner on 3/8 always has the post's column on its +x side (inside it at
	 * a convex corner, or the empty quarter on its -x side at a concave one), so it always moves to -x; likewise the rest.
	 */
	static double grown(double c, double grow) {
		return c < 0.5 ? c - grow : c + grow;
	}

	/**
	 * The shape's edges, 1 high, x1 y1 z1 x2 y2 z2 each: its footprint traced round as one polygon over a 3 x 3 grid of
	 * cells (the post in the middle, an arm in each joined side's cell), the bottom and top loops along it and an upright
	 * edge at each of its corners, so no edge is drawn inside the shape where an arm meets the post, as later versions
	 * draw it.
	 */
	static double[] outline(boolean west, boolean east, boolean north, boolean south) {
		double[] at = {0, LO, HI, 1};
		boolean[][] filled = new boolean[3][3]; // [x][z]
		filled[1][1] = true;
		filled[0][1] = west;
		filled[2][1] = east;
		filled[1][0] = north;
		filled[1][2] = south;
		// Directed edges round each filled cell, kept only where the cell across is empty. Grid corner (i, k) is i * 4 + k.
		// At most one edge leaves any corner, as no two filled cells meet at a corner alone.
		int[] next = new int[16];
		Arrays.fill(next, -1);
		for (int i = 0; i < 3; i++) {
			for (int k = 0; k < 3; k++) {
				if (!filled[i][k]) continue;
				if (!filled(filled, i, k - 1)) next[i * 4 + k] = (i + 1) * 4 + k;
				if (!filled(filled, i + 1, k)) next[(i + 1) * 4 + k] = (i + 1) * 4 + k + 1;
				if (!filled(filled, i, k + 1)) next[(i + 1) * 4 + k + 1] = i * 4 + k + 1;
				if (!filled(filled, i - 1, k)) next[i * 4 + k + 1] = i * 4 + k;
			}
		}
		int start = 0;
		while (next[start] < 0) start++;
		int[] previous = new int[16];
		for (int c = 0; c < 16; c++) {
			if (next[c] >= 0) previous[next[c]] = c;
		}
		List<Integer> corners = new ArrayList<>();
		int c = start;
		do {
			int p = previous[c], n = next[c];
			// Where the boundary goes straight on there's no corner.
			if ((c / 4 - p / 4) * (n % 4 - c % 4) != (c % 4 - p % 4) * (n / 4 - c / 4)) corners.add(c);
			c = n;
		} while (c != start);
		double[] edges = new double[corners.size() * 18];
		int e = 0;
		for (int i = 0; i < corners.size(); i++) {
			int a = corners.get(i), b = corners.get((i + 1) % corners.size());
			double ax = at[a / 4], az = at[a % 4], bx = at[b / 4], bz = at[b % 4];
			e = edge(edges, e, ax, 0, az, bx, 0, bz);
			e = edge(edges, e, ax, AIM_HEIGHT, az, bx, AIM_HEIGHT, bz);
			e = edge(edges, e, ax, 0, az, ax, AIM_HEIGHT, az);
		}
		return edges;
	}

	private static boolean filled(boolean[][] filled, int i, int k) {
		return i >= 0 && i < 3 && k >= 0 && k < 3 && filled[i][k];
	}

	private static int edge(double[] edges, int e, double x1, double y1, double z1, double x2, double y2, double z2) {
		edges[e] = x1;
		edges[e + 1] = y1;
		edges[e + 2] = z1;
		edges[e + 3] = x2;
		edges[e + 4] = y2;
		edges[e + 5] = z2;
		return e + 6;
	}
}
