package org.studioquire.oldfaithful.lighting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Raises cells that are too dark and spreads the light on from them, brightest first, as far as it reaches through
 * loaded chunks: a step into a neighbour costs the neighbour's opacity (at least 1), and a neighbour is raised only if
 * that leaves it brighter than it is. What a cell is raised to is what a neighbour passes on under the rule, so light
 * only ever comes up to where it belongs. Every changed cell is reported to the world's listeners as vanilla's
 * {@code World.setLight} does, so render chunks are rebuilt and a server sends the change to players.
 *
 * <p>Vanilla's updates also ask a changed cell's neighbours to update, and some light depends on that: the height map's
 * direct writes (a block placed over water zeroes the column below it) can leave a neighbour too bright, which is
 * put right only when the cell next to it changes again. So each neighbour of a raised cell that this doesn't raise
 * is checked, and one that's too bright goes to vanilla's queue, as vanilla's own update would have sent it.
 */
final class Spread {
	private final World world;
	private final Map<Long, Chunk> chunks = new HashMap<>();
	/** Per light type and level: the cells raised to that level, still to spread from. */
	private final Cells[][] levels = new Cells[2][16];

	Spread(World world) {
		this.world = world;
	}

	private static final class Cells {
		Chunk[] chunk = new Chunk[64];
		int[] index = new int[64];
		int size;

		void add(Chunk c, int i) {
			if (size == index.length) {
				chunk = Arrays.copyOf(chunk, size * 2);
				index = Arrays.copyOf(index, size * 2);
			}
			chunk[size] = c;
			index[size++] = i;
		}
	}

	/** Raises a cell (world coordinates, in chunk {@code c}) to {@code value}, to be spread from by {@link #run}. */
	void raise(int type, Chunk c, int x, int y, int z, int value) {
		c.setLight(type == 0 ? LightType.SKY : LightType.BLOCK, x & 15, y, z & 15, value);
		world.blockUpdateEvent(x, y, z);
		if (value > 0) {
			if (levels[type][value] == null) levels[type][value] = new Cells();
			levels[type][value].add(c, (x & 15) << 11 | (z & 15) << 7 | y);
		}
	}

	void run() {
		for (int type = 0; type < 2; type++) {
			// Brightest first. A step costs at least 1, but a light source it reaches is lit to its own brightness,
			// which can be above the level being spread: so after each level, start again from the top.
			for (int level = 15; level > 0; level--) {
				Cells cells = levels[type][level];
				if (cells == null) continue;
				for (int k = 0; k < cells.size; k++) spreadFrom(type, cells.chunk[k], cells.index[k], level);
				levels[type][level] = null;
				level = 16;
			}
		}
	}

	private void spreadFrom(int type, Chunk c, int index, int level) {
		if (Relight.light(type, c, index) != level) return; // raised further since
		int lx = index >> 11, lz = index >> 7 & 15, y = index & 127;
		int x = c.x * 16 + lx, z = c.z * 16 + lz;
		if (y > 0) step(type, c, x, y - 1, z, level);
		if (y < 127) step(type, c, x, y + 1, z, level);
		step(type, lx > 0 ? c : chunk(c.x - 1, c.z), x - 1, y, z, level);
		step(type, lx < 15 ? c : chunk(c.x + 1, c.z), x + 1, y, z, level);
		step(type, lz > 0 ? c : chunk(c.x, c.z - 1), x, y, z - 1, level);
		step(type, lz < 15 ? c : chunk(c.x, c.z + 1), x, y, z + 1, level);
	}

	private void step(int type, Chunk c, int x, int y, int z, int level) {
		if (c == null) return;
		int index = (x & 15) << 11 | (z & 15) << 7 | y;
		int id = c.blocks[index] & 255;
		int value = level - Math.max(Relight.OPACITY[id], 1);
		// A light source reached by light is lit (vanilla's update does the same; with lava.lightGenerated=false this is
		// how a dark body of lava comes on once light touches it).
		if (type == 1) value = Math.max(value, Relight.LUMINANCE[id]);
		int have = Relight.light(type, c, index);
		if (value > have) {
			raise(type, c, x, y, z, value);
			return;
		}
		if (have == 0) return; // can't be too bright
		Chunk west = (x & 15) > 0 ? c : chunk(c.x - 1, c.z), east = (x & 15) < 15 ? c : chunk(c.x + 1, c.z);
		Chunk north = (z & 15) > 0 ? c : chunk(c.x, c.z - 1), south = (z & 15) < 15 ? c : chunk(c.x, c.z + 1);
		if (west == null || east == null || north == null || south == null) return;
		int want = Relight.wanted(type, c, west, east, north, south, x & 15, y, z & 15, Relight.betaLava());
		if (want >= 0 && want < have) world.queueLightUpdate(type == 0 ? LightType.SKY : LightType.BLOCK, x, y, z, x, y, z);
	}

	private Chunk chunk(int chunkX, int chunkZ) {
		long key = (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
		if (chunks.containsKey(key)) return chunks.get(key);
		Chunk c = Relight.loaded(world, chunkX, chunkZ);
		chunks.put(key, c);
		return c;
	}
}
