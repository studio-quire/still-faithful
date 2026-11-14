package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.client.render.Tessellator;
import net.minecraft.world.World;

/** #30: a fence's outline drawn as lines along its post and arms ({@link Fences#outline}), in the state beta sets for its box. */
public final class FenceOutline {
	/** Beta grows the block's box by this, so the lines sit just outside its faces. */
	private static final double GROW = 0.002;

	private FenceOutline() {
	}

	public static void draw(World world, int x, int y, int z, double cameraX, double cameraY, double cameraZ) {
		double[] edges = Fences.outline(world, x, y, z, Block.FENCE, GROW);
		Tessellator t = Tessellator.INSTANCE;
		t.start(1);
		for (int i = 0; i < edges.length; i += 3) t.vertex(edges[i] - cameraX, edges[i + 1] - cameraY, edges[i + 2] - cameraZ);
		t.draw();
	}
}
