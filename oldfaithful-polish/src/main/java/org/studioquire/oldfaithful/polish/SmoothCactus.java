package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.world.BlockView;

/**
 * Cactus lit smoothly while the player's Smooth Lighting is on ({@code render.smoothCactus}), as later versions light it:
 * with ambient occlusion, from beta's own light. Beta's smooth lighting goes only to blocks drawn through renderBlock
 * (renderSmooth); renderCactus lights each face from one reading, the cell beyond it, so by a torch a cactus was a step a
 * face beside the smoothly lit sand.
 *
 * <p>Here each corner of a face averages four readings, as renderSmooth's do: the one beta lit the whole face with, and
 * the three around the corner in a plane of cells, the diagonal only where one of the two between lets light through
 * (renderSmooth's own rule, by Block.BLOCKS_ALLOW_VISION, standing in with the neighbour along the horizontal axis
 * otherwise, as renderSmooth does). Where the light is the same all round, every corner gets beta's one value.
 *
 * <p>The plane. Beta draws a cactus's sides a pixel in (its box is whole), so a side's plane is the cactus's own layer,
 * later versions' rule for a face inside its cell, and the block it stands on shades its foot as the ground does a
 * wall's; the middle reading stays beta's, the cell beyond. The top's and bottom's planes are the cells beyond them.
 *
 * <p>Each vertex takes its corner of the cell's face, so every vertex is four readings in quarters, which smooth dusk
 * (DuskLight) takes apart as it does renderSmooth's, and the speed mod's light cache answers the repeats.
 */
public final class SmoothCactus {
	private SmoothCactus() {
	}

	/** For the self-test only: cactus lit as beta lit it, from the next rebuild. */
	public static volatile boolean betaForTest;

	/** Beta's shade for each side (0 bottom, 1 top, 2 and 3 facing -z and +z, 4 and 5 facing -x and +x). */
	public static final float[] SHADE = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};

	private static final int[][] NORMAL = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
	/** Each side's plane: its first axis (the horizontal one renderSmooth falls back to), then its second. */
	private static final int[][] U = {{1, 0, 0}, {1, 0, 0}, {1, 0, 0}, {1, 0, 0}, {0, 0, 1}, {0, 0, 1}};
	private static final int[][] W = {{0, 0, 1}, {0, 0, 1}, {0, 1, 0}, {0, 1, 0}, {0, 1, 0}, {0, 1, 0}};
	/**
	 * Where renderBottomFace ... renderSouthFace put their four vertices, in the order they write them: for each side and
	 * vertex, whether it lies at the far end along the side's first axis, then along its second.
	 */
	private static final boolean[][] FAR_U = {
			{false, false, true, true}, {true, true, false, false}, {false, true, true, false},
			{false, false, true, true}, {true, false, false, true}, {true, false, false, true}};
	private static final boolean[][] FAR_W = {
			{true, false, false, true}, {true, false, false, true}, {true, true, false, false},
			{true, false, false, true}, {true, true, false, false}, {false, false, true, true}};

	/** Whether faces are lit smoothly now: Smooth Lighting on. */
	public static boolean on() {
		return !betaForTest && Minecraft.isAmbientOcclusionEnabled();
	}

	/**
	 * Whether renderCactus reads this face's light in the cactus's own cell rather than the one beyond: its tests, renderFlat's,
	 * which a cactus's whole box never meets.
	 */
	private static boolean readsOwnCell(Block b, int side) {
		switch (side) {
			case 0: return false;
			case 1: return b.maxY != 1.0 && !b.material.isFluid();
			case 2: return b.minZ > 0.0;
			case 3: return b.maxZ < 1.0;
			case 4: return b.minX > 0.0;
			default: return b.maxX < 1.0;
		}
	}

	/** The light at a face's four vertices, in the order its renderXFace writes them. */
	public static void face(Block cactus, BlockView view, int x, int y, int z, int side, float[] vertices) {
		int[] n = NORMAL[side];
		boolean own = readsOwnCell(cactus, side);
		float centre = own ? cactus.getLuminance(view, x, y, z) : cactus.getLuminance(view, x + n[0], y + n[1], z + n[2]);
		if (side < 2 && !own) plane(cactus, view, side, centre, x + n[0], y + n[1], z + n[2], vertices);
		else plane(cactus, view, side, centre, x, y, z, vertices);
	}

	/**
	 * Each vertex the corner of the plane of cells centred on (px, py, pz) it lies at, which averages {@code centre}, the
	 * neighbours along both axes toward it and the diagonal between them.
	 */
	private static void plane(Block block, BlockView view, int side, float centre, int px, int py, int pz, float[] vertices) {
		int[] u = U[side], w = W[side];
		float u0 = block.getLuminance(view, px - u[0], py - u[1], pz - u[2]);
		float u1 = block.getLuminance(view, px + u[0], py + u[1], pz + u[2]);
		float w0 = block.getLuminance(view, px - w[0], py - w[1], pz - w[2]);
		float w1 = block.getLuminance(view, px + w[0], py + w[1], pz + w[2]);
		boolean seeU0 = Block.BLOCKS_ALLOW_VISION[view.getBlockId(px - u[0], py - u[1], pz - u[2])];
		boolean seeU1 = Block.BLOCKS_ALLOW_VISION[view.getBlockId(px + u[0], py + u[1], pz + u[2])];
		boolean seeW0 = Block.BLOCKS_ALLOW_VISION[view.getBlockId(px - w[0], py - w[1], pz - w[2])];
		boolean seeW1 = Block.BLOCKS_ALLOW_VISION[view.getBlockId(px + w[0], py + w[1], pz + w[2])];
		float lowLow = corner(block, view, px, py, pz, u, w, -1, -1, centre, u0, w0, seeU0 || seeW0);
		float lowHigh = corner(block, view, px, py, pz, u, w, -1, 1, centre, u0, w1, seeU0 || seeW1);
		float highLow = corner(block, view, px, py, pz, u, w, 1, -1, centre, u1, w0, seeU1 || seeW0);
		float highHigh = corner(block, view, px, py, pz, u, w, 1, 1, centre, u1, w1, seeU1 || seeW1);
		for (int v = 0; v < 4; v++) {
			boolean highU = FAR_U[side][v], highW = FAR_W[side][v];
			vertices[v] = highU ? (highW ? highHigh : highLow) : (highW ? lowHigh : lowLow);
		}
	}

	private static float corner(Block block, BlockView view, int px, int py, int pz, int[] u, int[] w, int su, int sw, float centre,
			float alongU, float alongW, boolean diagonalSeen) {
		float diagonal = alongU;
		if (diagonalSeen) diagonal = block.getLuminance(view, px + su * u[0] + sw * w[0], py + su * u[1] + sw * w[1], pz + su * u[2] + sw * w[2]);
		return (centre + alongU + alongW + diagonal) / 4.0F;
	}
}
