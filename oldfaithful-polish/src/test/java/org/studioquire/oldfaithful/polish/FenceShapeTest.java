package org.studioquire.oldfaithful.polish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** #30's outline: one loop round the post and arms, corners only where it turns, nothing drawn inside the shape. */
class FenceShapeTest {
	@Test
	void outlines() {
		// west, east, north, south; corners; the bottom loop's length.
		shape(false, false, false, false, 4, 1.0);
		shape(true, true, false, false, 4, 2.5);
		shape(false, false, true, false, 4, 1.75);
		shape(false, true, false, true, 6, 2.5);
		shape(true, true, false, true, 8, 3.25);
		shape(true, true, true, true, 12, 4.0);
	}

	private static void shape(boolean w, boolean e, boolean n, boolean s, int corners, double perimeter) {
		double[] edges = FenceShape.outline(w, e, n, s);
		String name = (w ? "W" : "") + (e ? "E" : "") + (n ? "N" : "") + (s ? "S" : "");
		assertEquals(corners * 18, edges.length, name + ": edges for " + corners + " corners");
		double bottom = 0;
		for (int i = 0; i < edges.length; i += 6) {
			double dx = edges[i + 3] - edges[i], dy = edges[i + 4] - edges[i + 1], dz = edges[i + 5] - edges[i + 2];
			if (dy == 0 && edges[i + 1] == 0) bottom += Math.abs(dx) + Math.abs(dz);
			// Every horizontal edge has the shape on one side only: its middle, nudged across, is in on one side and out on the other.
			if (dy == 0) {
				double mx = (edges[i] + edges[i + 3]) / 2, mz = (edges[i + 2] + edges[i + 5]) / 2;
				double nx = dz != 0 ? 0.01 : 0, nz = dx != 0 ? 0.01 : 0;
				assertTrue(inside(w, e, n, s, mx + nx, mz + nz) != inside(w, e, n, s, mx - nx, mz - nz), name + ": an edge inside the shape at " + mx + " " + mz);
			}
		}
		assertEquals(perimeter, bottom, 1e-9, name + ": the bottom loop's length");
	}

	private static boolean inside(boolean w, boolean e, boolean n, boolean s, double x, double z) {
		for (double[] f : FenceShape.footprint(w, e, n, s)) {
			if (x > f[0] && x < f[2] && z > f[1] && z < f[3]) return true;
		}
		return false;
	}
}
