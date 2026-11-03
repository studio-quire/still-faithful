package org.studioquire.oldfaithful.polish;

import java.nio.ByteOrder;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.Block;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldRegion;

/**
 * #6, smooth dusk and dawn. Beta bakes the sky's brightness into each chunk's mesh: every vertex's colour is its tint
 * and shade times a luminance read from the light around it at the world's ambient darkness (0 to 11), so each of the
 * eleven steps at dusk and dawn re-meshed every sky-lit chunk in view. Here a chunk's mesh carries, per vertex, the
 * light it was lit from instead, and {@link DuskShader} applies the darkness when it's drawn, so nothing is rebuilt and
 * the darkness can move continuously. At a whole darkness the picture is beta's own baking at that darkness.
 *
 * <p>How the light gets into the mesh, without knowing how any block's renderer works. Each light level the game reads
 * for drawing is max(sky - darkness, block) per position (the slab rule and a liquid's two readings take maxima of
 * those, which keeps the form), so a position's light is a pair (sky, block), read here as the level at darkness 0
 * and at 15. A renderer's colours are linear in the luminances it reads: tint times shade times one luminance, or the
 * average of four (smooth lighting), or a constant (an emitting torch's 1.0). So each block is drawn first with every
 * luminance read as 1, giving each vertex's tint and shade (what the mesh keeps as its colour) and the pairs read, and
 * then, if it read more than one pair, again without output, each pair reading as its own power of five: a vertex's
 * colour divided by the first pass's is then its pairs' weights in quarters, written out in base five. Up to four
 * pairs per vertex go into the mesh (the tessellator's spare eighth int); the colour's lost fraction goes into the
 * normal's int, which chunk meshes don't use. Where every pair a block read ignores the darkness (out of the sky's
 * reach, lit by its own light), its colours are beta's at any darkness and are kept as drawn. A vertex that doesn't
 * decode (a modded renderer doing something else with the light) is drawn as beta drew it and its chunk keeps beta's
 * rebuild at each step.
 *
 * <p>Two ways to use it ({@link #smooth}): stepped draws at the world's whole darkness, beta's own pictures with the
 * whole view changing at once and no rebuild wave; smooth at the continuous darkness of the time of day.
 */
public final class DuskLight {
	private DuskLight() {
	}

	/** The tessellator (mixin/dusk/TessellatorMixin). */
	public interface Mesh {
		int[] oldfaithful$buffer();

		int oldfaithful$position();

		int oldfaithful$size();

		/** No quad half-written: a draw here splits nothing. */
		boolean oldfaithful$wholeQuads();

		/** Draws what's buffered and carries on, as the tessellator does itself when full. */
		void oldfaithful$flush();

		void oldfaithful$saveState();

		void oldfaithful$restoreState();
	}

	/** A render chunk (mixin/dusk/ChunkBuilderMixin): whether it holds vertices lit as beta lit them, at one darkness. */
	public interface Section {
		boolean oldfaithful$stepped();

		void oldfaithful$stepped(boolean stepped);
	}

	/** Statistics since start, for the self-test: blocks drawn once, with code passes, as beta's; vertices undecoded. */
	public static long blocksOnePass, blocksCoded, blocksStatic, codePasses, verticesFallback;

	/** For the self-test only: beta's lighting (baked, rebuilt at each step), from the next reload of the view. */
	public static volatile boolean betaForTest;
	/** For the self-test only: the darkness to draw at, NaN for the time of day's. */
	public static volatile float testDarkness = Float.NaN;

	/** 0 until the first chunk is built or drawn, then 1 with the shader working, -1 without (beta's lighting). */
	private static int state;
	private static boolean beta;

	/**
	 * Smooth (the darkness follows the time of day) or stepped (the world's own whole darkness, the one beta bakes and
	 * lights entities with, so every frame is beta's picture at its step, the whole view changing at once). From the
	 * config at start; the settings screen switches it live.
	 */
	public static volatile boolean smooth = "smooth".equals(PolishConfig.duskAtStart());

	/** This frame's darkness, 0 (day) to 11 (night): continuous when smooth, whole when stepped. */
	public static float darkness;
	/** Above 0 while entities, particles and the hand are drawn: their light follows {@link #darkness} too. */
	public static int entityScope;

	private static final int MARGIN = 1 << 16;
	/** A pair that isn't one (sky below block light, which reading never gives): luminance 1, whatever the darkness. */
	static final int FULL = 0x0F;
	private static final int GROUP = 6;
	private static final float[] CODES = {1, 5, 25, 125, 625, 3125, 15625};
	private static final boolean LITTLE = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
	private static final int LIGHT_PASS = 0, LIGHT_UNIT = 1, LIGHT_CODE = 2;

	// The rebuild under way.
	public static boolean building;
	private static World world;
	private static WorldRegion region;
	private static Section section;
	private static int minX, minY, minZ, sizeX, sizeY, sizeZ;
	private static int[] stamps = new int[0];
	private static byte[] pairs = new byte[0];
	private static int generation;

	// The block under way.
	private static int light;
	/** Drawing the block for real, every luminance 1: vertices are recorded as the tessellator writes them. */
	public static boolean real;
	/** Drawing the block again only to read its colours: the tessellator writes nothing. */
	public static boolean dry;
	private static boolean flushed, unknownPair;
	private static int group;
	private static final int[] index = new int[256];
	private static int[] read = new int[32];
	private static int reads;
	private static int vertices, dryVertices;
	private static int[] starts = new int[256], ends = new int[256];
	private static float[] unit = new float[768], again = new float[768];
	private static int[] found = new int[1024];
	private static int[] quarters = new int[256], constants = new int[256];

	// The tessellator's colour as last set, as floats (it keeps only bytes).
	private static float red, green, blue;
	private static boolean fromFloats;

	static {
		java.util.Arrays.fill(index, -1);
	}

	/** Whether chunks are drawn with the light applied at draw time; decided once, when the GL is first there. */
	public static boolean active() {
		if (state == 0) {
			state = DuskShader.init() ? 1 : -1;
			beta = betaForTest;
		}
		return state > 0 && !beta;
	}

	/** Beta's view reloaded (every chunk rebuilt): the self-test's switch between beta's lighting and this takes effect. */
	public static void reloaded() {
		beta = betaForTest;
	}

	// ---- The darkness

	/** At the start of each frame: the darkness for this frame's chunks, entities and particles. */
	public static void frame(World w, float tickDelta) {
		building = false;
		entityScope = 0;
		if (w == null) return;
		float test = testDarkness;
		if (!Float.isNaN(test)) {
			darkness = test;
			return;
		}
		if (!smooth) {
			// Whole, and the same value everything else beta draws reads (entities, particles, the fog's brightness),
			// so in each frame terrain and the rest are at one step, as beta's are once its rebuild has passed.
			darkness = w.ambientDarkness;
			return;
		}
		// The same daylight curve beta reads its whole darkness from, not cut to a whole step; kept within the step the
		// game itself is at, so it agrees with a mod that changes the curve at least to the step.
		float day = MathHelper.cos(w.getTime(tickDelta) * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
		day = Math.max(0.0F, Math.min(1.0F, day));
		day = (float) (day * (1.0 - w.getRainGradient(tickDelta) * 5.0F / 16.0));
		day = (float) (day * (1.0 - w.getThunderGradient(tickDelta) * 5.0F / 16.0));
		float d = (1.0F - day) * 11.0F;
		int step = w.getAmbientDarkness(tickDelta);
		darkness = Math.max(step, Math.min(Math.min(step + 1, 11), d));
	}

	// ---- Building a chunk

	public static void beginRebuild(Section s, WorldRegion r, World w, int x0, int y0, int z0, int x1, int y1, int z1) {
		building = active();
		if (!building) return;
		section = s;
		s.oldfaithful$stepped(false);
		region = r;
		world = w;
		minX = x0;
		minY = y0;
		minZ = z0;
		sizeX = x1 - x0 + 1;
		sizeY = y1 - y0 + 1;
		sizeZ = z1 - z0 + 1;
		int n = sizeX * sizeY * sizeZ;
		if (stamps.length < n) {
			stamps = new int[n];
			pairs = new byte[n];
			generation = 0;
		}
		if (++generation == 0) {
			java.util.Arrays.fill(stamps, 0);
			generation = 1;
		}
	}

	public static void endRebuild() {
		building = false;
		region = null;
		world = null;
		section = null;
	}

	/** Whether the region's light readings are this rebuild's to answer (in a pass that reads them its own way). */
	public static boolean answers(Object view) {
		return light != LIGHT_PASS && view == region;
	}

	/** One luminance a renderer reads: getNaturalBrightness's (block light at least the block's own) or, at -1, plain. */
	public static float luminance(int x, int y, int z, int ownLight) {
		int p = pair(x, y, z);
		if (ownLight > 0) {
			int b = Math.max(p & 15, Math.min(ownLight, 15));
			p = Math.max(p >> 4, b) << 4 | b;
		}
		return read(p);
	}

	/** A liquid's luminance: the brighter of its own position's and the one above (LiquidBlock.getLuminance). */
	public static float liquidLuminance(int x, int y, int z) {
		int a = pair(x, y, z), b = pair(x, y + 1, z);
		return read(Math.max(a & 0xF0, b & 0xF0) | Math.max(a & 15, b & 15));
	}

	/** The (sky, block) light at a position as the region reads it for drawing: its level at darkness 0 and at 15. */
	private static int pair(int x, int y, int z) {
		int dx = x - minX, dy = y - minY, dz = z - minZ;
		int slot = dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ ? -1 : (dy * sizeZ + dz) * sizeX + dx;
		if (slot >= 0 && stamps[slot] == generation) return pairs[slot] & 0xFF;
		int saved = world.ambientDarkness, lit, dark;
		try {
			world.ambientDarkness = 0;
			lit = region.getRawBrightness(x, y, z, true);
			world.ambientDarkness = 15;
			dark = region.getRawBrightness(x, y, z, true);
		} finally {
			world.ambientDarkness = saved;
		}
		lit = Math.max(0, Math.min(15, lit));
		dark = Math.max(0, Math.min(lit, dark));
		int p = lit << 4 | dark;
		if (slot >= 0) {
			stamps[slot] = generation;
			pairs[slot] = (byte) p;
		}
		return p;
	}

	private static float read(int p) {
		int j = index[p];
		if (light == LIGHT_UNIT) {
			if (j < 0) {
				if (reads == read.length) read = java.util.Arrays.copyOf(read, reads * 2);
				index[p] = reads;
				read[reads++] = p;
			}
			return 1.0F;
		}
		if (j < 0) {
			unknownPair = true;
			return 0.0F;
		}
		int k = j - group * GROUP;
		return k >= 0 && k < GROUP ? CODES[k + 1] : 0.0F;
	}

	/** Drawing one block into a chunk (ChunkBuilder.rebuild's call). */
	public static boolean render(BlockRenderManager renderer, Block block, int x, int y, int z, Operation<Boolean> original) {
		if (!building) return original.call(renderer, block, x, y, z);
		Mesh mesh = (Mesh) Tessellator.INSTANCE;
		if (mesh.oldfaithful$position() > mesh.oldfaithful$size() - MARGIN && mesh.oldfaithful$wholeQuads()) mesh.oldfaithful$flush();
		reads = 0;
		vertices = 0;
		flushed = false;
		boolean drawn;
		light = LIGHT_UNIT;
		real = true;
		try {
			drawn = original.call(renderer, block, x, y, z);
		} finally {
			light = LIGHT_PASS;
			real = false;
		}
		try {
			if (vertices > 0) finish(mesh, renderer, block, x, y, z, original);
		} finally {
			for (int i = 0; i < reads; i++) index[read[i]] = -1;
			reads = 0;
		}
		return drawn;
	}

	/** Torches, levers and repeaters can draw parts at a constant brightness, whatever they read. */
	private static boolean mayBeConstant(Block block) {
		int type = block.getRenderType();
		return type == 2 || type == 12 || type == 15 || type > 17;
	}

	private static void finish(Mesh mesh, BlockRenderManager renderer, Block block, int x, int y, int z, Operation<Boolean> original) {
		int[] buf = mesh.oldfaithful$buffer();
		if (flushed) {
			// The tessellator drew part of this block before it was done (never for a vanilla block: MARGIN is far more
			// than any draws). Those vertices are gone; at least the chunk is rebuilt at each step.
			section.oldfaithful$stepped(true);
			verticesFallback += vertices;
			return;
		}
		if (reads == 0 || reads == 1 && !mayBeConstant(block)) {
			int p = reads == 0 ? FULL : read[0];
			int four = four(p, p, p, p);
			for (int v = 0; v < vertices; v++) write(buf, v, four, unit, false);
			blocksOnePass++;
			return;
		}
		boolean sensitive = false;
		for (int i = 0; i < reads; i++) sensitive |= (read[i] >> 4) > (read[i] & 15);
		if (!sensitive) {
			asBeta(mesh, buf, renderer, block, x, y, z, original, false);
			return;
		}
		java.util.Arrays.fill(quarters, 0, vertices, 0);
		if (found.length < vertices * 4) found = new int[vertices * 4];
		int groups = (reads + GROUP - 1) / GROUP;
		boolean ok = true;
		for (group = 0; group < groups && ok; group++) {
			ok = dryPass(mesh, renderer, block, x, y, z, original, LIGHT_CODE) && decode();
		}
		group = 0;
		if (!ok) {
			asBeta(mesh, buf, renderer, block, x, y, z, original, true);
			return;
		}
		for (int v = 0; v < vertices; v++) {
			int f = v * 4;
			write(buf, v, four(found[f], found[f + 1], found[f + 2], found[f + 3]), unit, false);
		}
		blocksCoded++;
	}

	/** The block's colours as beta draws them now, kept: exact at every darkness when nothing it read follows the sky. */
	private static void asBeta(Mesh mesh, int[] buf, BlockRenderManager renderer, Block block, int x, int y, int z,
			Operation<Boolean> original, boolean stepped) {
		if (!dryPass(mesh, renderer, block, x, y, z, original, LIGHT_PASS)) {
			// Drawn differently a second time: nothing better than the first pass's colours, rebuilt at each step.
			stepped = true;
			System.arraycopy(unit, 0, again, 0, vertices * 3);
		}
		int four = four(FULL, FULL, FULL, FULL);
		for (int v = 0; v < vertices; v++) write(buf, v, four, again, true);
		if (stepped) {
			section.oldfaithful$stepped(true);
			verticesFallback += vertices;
		}
		blocksStatic++;
	}

	private static boolean dryPass(Mesh mesh, BlockRenderManager renderer, Block block, int x, int y, int z,
			Operation<Boolean> original, int mode) {
		float r = red, g = green, b = blue;
		boolean f = fromFloats;
		mesh.oldfaithful$saveState();
		dryVertices = 0;
		unknownPair = false;
		light = mode;
		dry = true;
		try {
			original.call(renderer, block, x, y, z);
		} finally {
			light = LIGHT_PASS;
			dry = false;
			mesh.oldfaithful$restoreState();
			red = r;
			green = g;
			blue = b;
			fromFloats = f;
		}
		if (mode == LIGHT_CODE) codePasses++;
		return dryVertices == vertices && !unknownPair;
	}

	/** One code pass's colours over the unit pass's: each vertex's weights for this group's pairs. */
	private static boolean decode() {
		for (int v = 0; v < vertices; v++) {
			int c = v * 3;
			int ch = unit[c] >= unit[c + 1] ? (unit[c] >= unit[c + 2] ? 0 : 2) : unit[c + 1] >= unit[c + 2] ? 1 : 2;
			double base = unit[c + ch];
			if (base <= 0.0) {
				// Black whatever the light: any weights do.
				if (group == 0) {
					quarters[v] = 4;
					for (int k = 0; k < 4; k++) found[v * 4 + k] = FULL;
				}
				continue;
			}
			double n = 4.0 * again[c + ch] / base;
			long q = Math.round(n);
			if (Math.abs(n - q) > 0.1) return false;
			// Base five, lowest digit first: the constant part (luminance 1), then this group's pairs.
			int constant = (int) (q % 5);
			q /= 5;
			if (group == 0) {
				constants[v] = constant;
				if (!add(v, FULL, constant)) return false;
			} else if (constant != constants[v]) {
				return false;
			}
			for (int k = 0; q > 0; k++, q /= 5) {
				int j = group * GROUP + k;
				int digit = (int) (q % 5);
				if (digit == 0) continue;
				if (k >= GROUP || j >= reads) return false;
				if (!add(v, read[j], digit)) return false;
			}
		}
		if (group == (reads + GROUP - 1) / GROUP - 1) {
			for (int v = 0; v < vertices; v++) {
				if (quarters[v] != 4) return false;
			}
		}
		return true;
	}

	private static boolean add(int v, int p, int count) {
		for (int i = 0; i < count; i++) {
			if (quarters[v] == 4) return false;
			found[v * 4 + quarters[v]++] = p;
		}
		return true;
	}

	// ---- The tessellator's side

	public static void colorFloats(float r, float g, float b) {
		red = r;
		green = g;
		blue = b;
		fromFloats = true;
	}

	public static void colorInts(int r, int g, int b) {
		if (fromFloats) {
			fromFloats = false;
			return;
		}
		red = Math.max(0, Math.min(255, r)) / 255.0F;
		green = Math.max(0, Math.min(255, g)) / 255.0F;
		blue = Math.max(0, Math.min(255, b)) / 255.0F;
	}

	/** A vertex in the real pass: where its records start. */
	public static void vertexStart(int position) {
		if (vertices == starts.length) {
			starts = java.util.Arrays.copyOf(starts, vertices * 2);
			ends = java.util.Arrays.copyOf(ends, vertices * 2);
			unit = java.util.Arrays.copyOf(unit, vertices * 6);
			again = java.util.Arrays.copyOf(again, vertices * 6);
			quarters = java.util.Arrays.copyOf(quarters, vertices * 2);
			constants = java.util.Arrays.copyOf(constants, vertices * 2);
		}
		starts[vertices] = position;
		unit[vertices * 3] = red;
		unit[vertices * 3 + 1] = green;
		unit[vertices * 3 + 2] = blue;
	}

	public static void vertexEnd(int position) {
		ends[vertices++] = position;
	}

	/** A vertex in a dry pass: only its colour. */
	public static void dryVertex() {
		int v = dryVertices++;
		if (v < vertices) {
			again[v * 3] = red;
			again[v * 3 + 1] = green;
			again[v * 3 + 2] = blue;
		}
	}

	/** The tessellator drew mid-block. */
	public static void drawn() {
		if (real) flushed = true;
	}

	/**
	 * A vertex's records: its pairs (int 7), its colour's fraction below the byte (int 6) and, if asked, its colour from
	 * {@code colours}. The quad's other triangle repeats two earlier vertices (the tessellator's quads-to-triangles), so
	 * those copies are refreshed from their originals, which are written by then.
	 */
	private static void write(int[] buf, int v, int four, float[] colours, boolean colour) {
		int start = starts[v], own = ends[v] - 8;
		float r = colours[v * 3], g = colours[v * 3 + 1], b = colours[v * 3 + 2];
		if (colour) {
			int old = buf[own + 5];
			int alpha = LITTLE ? old >>> 24 : old & 0xFF;
			buf[own + 5] = bytes(whole(r), whole(g), whole(b), alpha, LITTLE);
		}
		buf[own + 6] = bytes(fine(r), fine(g), fine(b), 0, LITTLE);
		buf[own + 7] = four;
		if (own - start == 16) {
			copy(buf, start, start - 24);
			copy(buf, start + 8, start - 8);
		}
	}

	private static void copy(int[] buf, int to, int from) {
		buf[to + 5] = buf[from + 5];
		buf[to + 6] = buf[from + 6];
		buf[to + 7] = buf[from + 7];
	}

	/** Four bytes in memory order. */
	private static int bytes(int b0, int b1, int b2, int b3, boolean little) {
		return little ? b0 | b1 << 8 | b2 << 16 | b3 << 24 : b0 << 24 | b1 << 16 | b2 << 8 | b3;
	}

	private static int four(int p0, int p1, int p2, int p3) {
		return bytes(p0, p1, p2, p3, LITTLE);
	}

	/** The colour byte the tessellator stores. */
	private static int whole(float c) {
		return Math.max(0, Math.min(255, (int) (c * 255.0F)));
	}

	/** The next eight bits below it, so the shader has the colour to 1/65280. */
	private static int fine(float c) {
		float x = c * 255.0F;
		if (x <= 0.0F) return 0;
		if (x >= 255.0F) return 255;
		return Math.min(255, (int) ((x - (int) x) * 256.0F));
	}
}
