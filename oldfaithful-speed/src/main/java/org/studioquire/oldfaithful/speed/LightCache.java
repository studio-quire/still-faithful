package org.studioquire.oldfaithful.speed;

/**
 * Light levels already read during one chunk rebuild, by position in the rebuild's region. Smooth lighting asks for the
 * same few positions' light many times per block (each corner of each face averages its neighbours); the region and
 * the world don't change during a rebuild, so the first answer stands. One per thread, reused: a generation stamp
 * empties it without clearing.
 */
public final class LightCache {
	private static final ThreadLocal<LightCache> CACHES = ThreadLocal.withInitial(LightCache::new);

	private int[] stamps = new int[0];
	private byte[] levels = new byte[0];
	private int generation;
	private int minX, minY, minZ, sizeX, sizeY, sizeZ;

	public static LightCache begin(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		LightCache c = CACHES.get();
		c.minX = minX;
		c.minY = minY;
		c.minZ = minZ;
		c.sizeX = maxX - minX + 1;
		c.sizeY = maxY - minY + 1;
		c.sizeZ = maxZ - minZ + 1;
		int n = c.sizeX * c.sizeY * c.sizeZ;
		if (c.stamps.length < n) {
			c.stamps = new int[n];
			c.levels = new byte[n];
			c.generation = 0;
		}
		if (++c.generation == 0) {
			java.util.Arrays.fill(c.stamps, 0);
			c.generation = 1;
		}
		return c;
	}

	/** The slot for a position, or -1 outside the region. */
	public int slot(int x, int y, int z) {
		int dx = x - minX, dy = y - minY, dz = z - minZ;
		if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) return -1;
		return (dy * sizeZ + dz) * sizeX + dx;
	}

	/** The level stored at slot this rebuild, or -1. */
	public int get(int slot) {
		return stamps[slot] == generation ? levels[slot] : -1;
	}

	public void put(int slot, int level) {
		stamps[slot] = generation;
		levels[slot] = (byte) level;
	}
}
