package org.studioquire.oldfaithful.lighting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Checks chunks against b1.7.3's light rule as they enter the world, and queues every cell that breaks it.
 *
 * <p>The rule is the one {@code LightUpdate.updateLight} applies: sky light is 15 at or above the column's height,
 * block light starts at the block's luminance, and otherwise a cell takes its brightest neighbour less its own opacity
 * (at least 1), never below 0; an opaque block that gives no light is 0. When every cell agrees with its neighbours
 * that way, the light is exactly right (each step costs at least 1, so wrong light can't hold itself up), and any
 * wrong light shows somewhere as a cell that disagrees. So a chunk is right once each of its cells has been checked
 * with all six neighbours present and every disagreeing one has been through vanilla's own update, which spreads the
 * change as far as it goes.
 *
 * <p>Vanilla loses that work in a few places (docs/bugs.md #3, docs/lighting.md): a chunk's own light is computed
 * before it's in the world, so the updates it asks for are dropped; generated lava never lights anything; and light
 * near the edge of the loaded area is computed against neighbours that aren't there. When a chunk enters the world,
 * then, its cells are checked, and so are the cells facing it in its loaded neighbours (whose check was waiting for
 * it). A cell whose neighbours aren't all loaded yet is checked when the last of them enters.
 *
 * <p>A cell that's too dark is lit here at once, and the light spread from it as far as it reaches ({@link Spread}),
 * so a chunk is lit when its check returns. (Sent through vanilla's queue, as at first, it waited: the single player
 * client works through 500 updates a frame, newest first, so while new chunks kept coming, the far ones' light,
 * generated lava above all, sat at the bottom of the queue until something near the player touched it. 2026-11-02.)
 * A cell that's too bright goes through vanilla's queue, whose updates bring light down. All of this runs at the
 * start of {@code doLightingUpdates}, never while a chunk is being meshed.
 */
public final class Relight {
	static final int[] OPACITY = Block.BLOCKS_LIGHT_OPACITY;
	static final int[] LUMINANCE = Block.BLOCKS_LIGHT_LUMINANCE;

	/**
	 * What a world's next lighting pass checks: chunks that entered it, and the boxes decoration asked to update (see
	 * {@link #decorating}); see {@link LightingWorld}.
	 */
	public static final class Entered {
		final List<Chunk> chunks = new ArrayList<>();
		/** type (0 sky, 1 block), minX, minY, minZ, maxX, maxY, maxZ */
		final List<int[]> boxes = new ArrayList<>();
	}

	private static int decorating;

	private Relight() {
	}

	public static void entered(World world, Chunk chunk) {
		if (world.isRemote || chunk.isEmpty()) return;
		((LightingWorld) world).oldfaithful$entered().chunks.add(chunk);
	}

	/**
	 * Decoration places things after a chunk has entered and been checked (a chunk is decorated once its neighbours on
	 * one side are loaded, which may be much later), and its light requests go to the back of vanilla's queue, which
	 * the single player client works through at 500 a frame, newest first: so a lava lake or a glowstone cluster far
	 * away waited dark. Requests made while decorating are noted, and their cells checked at the next pass like an
	 * entering chunk's.
	 */
	public static void decorating(boolean start) {
		decorating = start ? decorating + 1 : Math.max(0, decorating - 1);
	}

	/** From {@code World.queueLightUpdate}: notes a request made while decorating. */
	public static void requested(World world, LightType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		if (decorating == 0 || world.isRemote) return;
		if (type == LightType.SKY && world.dimension.hasCeiling) return;
		((LightingWorld) world).oldfaithful$entered().boxes.add(new int[] {type == LightType.SKY ? 0 : 1, minX, minY, minZ, maxX, maxY, maxZ});
	}

	/** Checks every chunk that entered since the last call. On the game thread, at the start of a lighting pass. */
	public static void checkEntered(World world) {
		decorating = 0; // decoration never spans a pass; an exception mid-decoration mustn't leave it set
		Entered entered = ((LightingWorld) world).oldfaithful$entered();
		if (!entered.boxes.isEmpty()) checkBoxes(world, entered.boxes);
		List<Chunk> list = entered.chunks;
		if (list.isEmpty()) return;
		Chunk[] chunks = list.toArray(new Chunk[0]);
		list.clear();
		long start = Stats.ENABLED ? System.nanoTime() : 0;
		for (Chunk c : chunks) {
			if (!c.loaded || loaded(world, c.x, c.z) != c) continue;
			Neighbourhood n = new Neighbourhood(world);
			n.spread = new Spread(world);
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) checkColumn(world, n, c.x * 16 + x, c.z * 16 + z);
			}
			// The columns facing this chunk in each loaded neighbour.
			for (int i = 0; i < 16; i++) {
				checkColumn(world, n, c.x * 16 - 1, c.z * 16 + i);
				checkColumn(world, n, c.x * 16 + 16, c.z * 16 + i);
				checkColumn(world, n, c.x * 16 + i, c.z * 16 - 1);
				checkColumn(world, n, c.x * 16 + i, c.z * 16 + 16);
			}
			n.spread.run();
		}
		if (Stats.ENABLED) Stats.checked(chunks.length, System.nanoTime() - start);
	}

	/** Cells in the noted boxes that are too dark are lit now (too bright ones are in vanilla's queue already). */
	private static void checkBoxes(World world, List<int[]> list) {
		int[][] boxes = list.toArray(new int[0][]);
		list.clear();
		Neighbourhood n = new Neighbourhood(world);
		n.spread = new Spread(world);
		for (int[] b : boxes) {
			int type = b[0];
			for (int x = b[1]; x <= b[4]; x++) {
				for (int z = b[3]; z <= b[6]; z++) {
					Chunk self = n.get(x >> 4, z >> 4);
					if (self == null) continue;
					Chunk west = n.get(x - 1 >> 4, z >> 4), east = n.get(x + 1 >> 4, z >> 4);
					Chunk north = n.get(x >> 4, z - 1 >> 4), south = n.get(x >> 4, z + 1 >> 4);
					if (west == null || east == null || north == null || south == null) continue;
					for (int y = Math.max(0, b[2]); y <= Math.min(127, b[5]); y++) {
						// Vanilla's queue recomputes every cell of these boxes in full, generated lava included.
						int want = wanted(type, self, west, east, north, south, x & 15, y, z & 15, false);
						if (want > light(type, self, x & 15, y, z & 15)) n.spread.raise(type, self, x, y, z, want);
					}
				}
			}
		}
		n.spread.run();
	}

	/** The loaded, real chunk at these chunk coordinates, or null; never loads or generates one. */
	static Chunk loaded(World world, int chunkX, int chunkZ) {
		if (!world.isPosLoaded(chunkX << 4, 64, chunkZ << 4)) return null;
		Chunk c = world.getChunk(chunkX, chunkZ);
		return c == null || c.isEmpty() ? null : c;
	}

	/** A small memo of the chunks around the columns being checked. */
	private static final class Neighbourhood {
		final World world;
		final long[] keys = new long[9];
		final Chunk[] chunks = new Chunk[9];
		int size;
		Spread spread;

		Neighbourhood(World world) {
			this.world = world;
		}

		Chunk get(int chunkX, int chunkZ) {
			long key = (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
			for (int i = 0; i < size; i++) if (keys[i] == key) return chunks[i];
			Chunk c = loaded(world, chunkX, chunkZ);
			if (size == keys.length) size = 0;
			keys[size] = key;
			chunks[size++] = c;
			return c;
		}
	}

	private static void checkColumn(World world, Neighbourhood n, int x, int z) {
		Chunk self = n.get(x >> 4, z >> 4);
		if (self == null) return;
		Chunk west = n.get(x - 1 >> 4, z >> 4), east = n.get(x + 1 >> 4, z >> 4);
		Chunk north = n.get(x >> 4, z - 1 >> 4), south = n.get(x >> 4, z + 1 >> 4);
		if (west == null || east == null || north == null || south == null) return; // checked when the last one enters
		boolean sky = !world.dimension.hasCeiling, betaLava = betaLava();
		for (int type = sky ? 0 : 1; type < 2; type++) {
			int run = -1;
			for (int y = 0; y <= 128; y++) {
				int want = y < 128 ? wanted(type, self, west, east, north, south, x & 15, y, z & 15, betaLava) : -1;
				if (want > light(type, self, x & 15, y, z & 15)) {
					// Too dark: lit now. (Cells raised by this spread are read as raised further up the column.)
					n.spread.raise(type, self, x, y, z, want);
					if (Stats.ENABLED) Stats.wrong(1);
					want = -1;
				}
				boolean wrong = want >= 0;
				if (wrong && run < 0) run = y;
				if (!wrong && run >= 0) {
					world.queueLightUpdate(type == 0 ? LightType.SKY : LightType.BLOCK, x, run, z, x, y - 1, z);
					if (Stats.ENABLED) Stats.wrong(y - run);
					run = -1;
				}
			}
		}
	}

	/** Whether the cell's stored light breaks the rule, given the chunks holding its six neighbours. */
	static boolean disagrees(int type, Chunk self, Chunk west, Chunk east, Chunk north, Chunk south, int lx, int y, int lz) {
		return wanted(type, self, west, east, north, south, lx, y, lz, betaLava()) >= 0;
	}

	/**
	 * With {@code lava.lightGenerated=false}: lava that has never been lit (block light 0) and has no lit neighbour
	 * counts as giving no light, as beta leaves lava that comes with new terrain. Once light reaches it (a lit
	 * neighbour) or something updates it, it's a source like any lava, and the light spreads through the whole body
	 * of lava touching it, as vanilla's own updates spread it. Only lava: everything else that gives light was always
	 * lit when placed.
	 */
	static boolean betaLava() {
		return !LightingConfig.lightGeneratedLava();
	}

	static boolean isLava(int id) {
		return id == 10 || id == 11; // flowing and still lava
	}

	/** The light the rule gives the cell when its stored light differs from it; -1 when they agree. */
	static int wanted(int type, Chunk self, Chunk west, Chunk east, Chunk north, Chunk south, int lx, int y, int lz, boolean betaLava) {
		int index = lx << 11 | lz << 7 | y;
		int id = self.blocks[index] & 255;
		int source = type == 0 ? (y >= (self.heightmap[lz << 4 | lx] & 255) ? 15 : 0) : LUMINANCE[id];
		int opacity = Math.max(OPACITY[id], 1);
		int have = light(type, self, index);
		boolean unlitLava = betaLava && type == 1 && have == 0 && isLava(id);
		int want;
		if (!unlitLava && (source == 15 || opacity >= 15 && source == 0)) {
			want = source;
		} else {
			int brightest = Math.max(y > 0 ? light(type, self, index - 1) : have, y < 127 ? light(type, self, index + 1) : have);
			brightest = Math.max(brightest, lx > 0 ? light(type, self, index - (1 << 11)) : light(type, west, 15 << 11 | lz << 7 | y));
			brightest = Math.max(brightest, lx < 15 ? light(type, self, index + (1 << 11)) : light(type, east, lz << 7 | y));
			brightest = Math.max(brightest, lz > 0 ? light(type, self, index - (1 << 7)) : light(type, north, lx << 11 | 15 << 7 | y));
			brightest = Math.max(brightest, lz < 15 ? light(type, self, index + (1 << 7)) : light(type, south, lx << 11 | y));
			if (unlitLava) want = brightest > 0 ? source : 0; // reached by light: it lights, as vanilla's update lights it
			else want = Math.max(source, Math.max(brightest - opacity, 0));
		}
		return have != want ? want : -1;
	}

	static int light(int type, Chunk c, int lx, int y, int lz) {
		return light(type, c, lx << 11 | lz << 7 | y);
	}

	static int light(int type, Chunk c, int index) {
		byte b = (type == 0 ? c.skyLight : c.blockLight).bytes[index >> 1];
		return (index & 1) == 0 ? b & 15 : b >> 4 & 15;
	}
}
