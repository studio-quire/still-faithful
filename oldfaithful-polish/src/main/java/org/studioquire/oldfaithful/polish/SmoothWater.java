package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.world.BlockView;

/**
 * Water lit smoothly while the player's Smooth Lighting is on ({@code render.smoothWater}). Beta's smooth lighting goes
 * only to full blocks (renderSmooth); renderFluid lights each face from one reading, the fluid's own: the brighter of a
 * cell and the one above it (LiquidBlock.getLuminance), at the fluid's cell for the top, at the cell beside for a side,
 * at the cell below for the bottom. So torchlight lay on a pond in squares and a tree's shade in blocks.
 *
 * <p>Here each corner of a face averages four of those readings, as renderSmooth averages four at a block's corner: the
 * face's own cell (beta's reading for the whole face) and the three around the corner in the same plane, the diagonal
 * one only where light can get round to it (renderSmooth's rule, asked here as whether one of the two cells between
 * isn't solid: renderSmooth asks whether one is see-through, which water and glass aren't to it). For the top the plane
 * is the fluid's own layer, read as beta reads the top (each column's cell and the one above, so the light just over the
 * surface, which lies near the top of the cell); for a side, the cells beside it; for the bottom, those below. Where the
 * light is the same all round, every corner gets beta's one value, so only where it varies does anything change.
 *
 * <p>The player's choice ({@link #look}): Shaded, the default, where a cell whose reading and the one above are both
 * solid (a bank a block high, a wall, a post or log standing in the water) reads dark, as a solid block does in
 * renderSmooth, so water shades in against it as the ground beside it does; Even, where such a cell reads as the face's
 * own, so nothing solid darkens the water; or Beta. While the mixins are in, all three switch at once, Beta by standing
 * aside; a game started at Beta has none of them in, so leaving Beta waits for a restart.
 *
 * <p>Only water: lava lights itself, so its top reads full light everywhere already, and averaging in the darker cells
 * at its shores only dimmed its rim (docs/still-faithful.md > Smooth water).
 *
 * <p>Four readings averaged, each a reading the game already makes: linear in the luminances, in quarters, so smooth
 * dusk (DuskLight) takes the light apart per vertex as it does renderSmooth's, and the speed mod's light cache answers
 * the repeats. In Fast graphics beta draws water's layer without blending colours across a face; mixin/smoothwater's
 * GameRendererMixin blends it while this is on.
 */
public final class SmoothWater {
	private SmoothWater() {
	}

	/** The config's values for the choice ({@code render.smoothWater}): true and false as for the switches (Shaded, Beta), and even. */
	public static final String SHADED = "true", EVEN = "even", BETA = "false";

	/**
	 * How water is lit now: SHADED, EVEN or BETA. From the file at start; {@link #choose}
	 * changes it and redraws. The self-test also sets it directly, for timing rebuilds without a redraw of everything.
	 */
	public static volatile String look = PolishConfig.smoothWaterAtStart();
	/** The chunks in view are to be rebuilt before the next frame (GameRendererMixin), the look having changed. */
	public static volatile boolean redraw;
	/** For the self-test only: lava lit smoothly too, to see what that does. */
	public static volatile boolean lavaForTest;

	/** The settings screen's choice, at once: the chunks in view are rebuilt in it before the next frame. */
	public static void choose(String value) {
		look = PolishConfig.smoothWaterMode(value);
		redraw = true;
	}

	/** Whether this fluid block, drawn now, is lit smoothly. */
	public static boolean applies(Block block) {
		if (!blends()) return false;
		Material m = block.material;
		return m == Material.WATER || lavaForTest && m == Material.LAVA;
	}

	/** Whether water is lit smoothly now (Smooth Lighting on), so its layer is drawn with colours blended. */
	public static boolean blends() {
		return !BETA.equals(look) && Minecraft.isAmbientOcclusionEnabled();
	}

	/** Where a vertex's light is in a face's four: its place along the face's first axis, then its second. */
	public static int corner(boolean highU, boolean highW) {
		return (highU ? 2 : 0) | (highW ? 1 : 0);
	}

	/** The top's corners: first axis x, second z. */
	public static void top(Block fluid, BlockView view, int x, int y, int z, float[] out) {
		plane(fluid, view, x, y, z, 1, 0, 0, 0, 0, 1, out);
	}

	/** A side's corners, toward (x + dx, z + dz): first axis along the face (x or z), second y. */
	public static void side(Block fluid, BlockView view, int x, int y, int z, int dx, int dz, float[] out) {
		if (dz != 0) plane(fluid, view, x, y, z + dz, 1, 0, 0, 0, 1, 0, out);
		else plane(fluid, view, x + dx, y, z, 0, 0, 1, 0, 1, 0, out);
	}

	/** The bottom's corners: first axis x, second z. */
	public static void bottom(Block fluid, BlockView view, int x, int y, int z, float[] out) {
		plane(fluid, view, x, y - 1, z, 1, 0, 0, 0, 0, 1, out);
	}

	/**
	 * The four corners of a face whose plane of cells is centred on (cx, cy, cz), with axes u and w: each the average of
	 * the centre, its neighbours along u and w toward the corner, and the diagonal between them.
	 */
	private static void plane(Block fluid, BlockView view, int cx, int cy, int cz, int ux, int uy, int uz, int wx, int wy, int wz, float[] out) {
		boolean even = EVEN.equals(look);
		float centre = fluid.getLuminance(view, cx, cy, cz);
		boolean openU0 = open(view, cx - ux, cy - uy, cz - uz), openU1 = open(view, cx + ux, cy + uy, cz + uz);
		boolean openW0 = open(view, cx - wx, cy - wy, cz - wz), openW1 = open(view, cx + wx, cy + wy, cz + wz);
		float u0 = read(fluid, view, cx - ux, cy - uy, cz - uz, even && !openU0, centre), u1 = read(fluid, view, cx + ux, cy + uy, cz + uz, even && !openU1, centre);
		float w0 = read(fluid, view, cx - wx, cy - wy, cz - wz, even && !openW0, centre), w1 = read(fluid, view, cx + wx, cy + wy, cz + wz, even && !openW1, centre);
		for (int i = 0; i < 2; i++) {
			int su = i == 0 ? -1 : 1;
			float u = i == 0 ? u0 : u1;
			boolean openU = i == 0 ? openU0 : openU1;
			for (int j = 0; j < 2; j++) {
				int sw = j == 0 ? -1 : 1;
				float w = j == 0 ? w0 : w1;
				float diagonal = u;
				if (openU || (j == 0 ? openW0 : openW1)) {
					int x = cx + su * ux + sw * wx, y = cy + su * uy + sw * wy, z = cz + su * uz + sw * wz;
					diagonal = read(fluid, view, x, y, z, even && !open(view, x, y, z), centre);
				}
				out[corner(i == 1, j == 1)] = (centre + u + w + diagonal) / 4.0F;
			}
		}
	}

	/** The fluid's reading there, or, for a solid cell under Even, the face's own. */
	private static float read(Block fluid, BlockView view, int x, int y, int z, boolean asCentre, float centre) {
		return asCentre ? centre : fluid.getLuminance(view, x, y, z);
	}

	/** Whether light reaches the fluid's reading there: the cell or the one above it isn't a solid block. */
	private static boolean open(BlockView view, int x, int y, int z) {
		return !Block.BLOCKS_OPAQUE[view.getBlockId(x, y, z)] || !Block.BLOCKS_OPAQUE[view.getBlockId(x, y + 1, z)];
	}
}
