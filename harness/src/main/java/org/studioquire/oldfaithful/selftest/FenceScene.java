package org.studioquire.oldfaithful.selftest;

import net.minecraft.block.Block;
import net.minecraft.world.World;

/**
 * Still Faithful's #30 (fences shaped as later versions shape them): the fences {@link FenceWalk} walks into, built by
 * whichever side runs the world (the single-player client, or the test server on "!oftest fences"). On a stone floor at
 * {@code floor}, the fences on the block above it: a lone post at the origin, a line of three 6 to 8 east of it, and a
 * corner 12 east (fences at 12 and 13 east, and 12 east 1 south).
 */
public final class FenceScene {
	public static final int LINE = 6, CORNER = 12;

	private FenceScene() {
	}

	public static void build(World world, int ox, int floor, int oz) {
		for (int x = ox - 4; x <= ox + CORNER + 6; x++) {
			for (int z = oz - 4; z <= oz + 7; z++) {
				world.setBlock(x, floor, z, Block.STONE.id);
				for (int y = floor + 1; y <= floor + 5; y++) world.setBlock(x, y, z, 0);
			}
		}
		world.setBlock(ox, floor + 1, oz, Block.FENCE.id);
		for (int dx = 0; dx < 3; dx++) world.setBlock(ox + LINE + dx, floor + 1, oz, Block.FENCE.id);
		world.setBlock(ox + CORNER, floor + 1, oz, Block.FENCE.id);
		world.setBlock(ox + CORNER + 1, floor + 1, oz, Block.FENCE.id);
		world.setBlock(ox + CORNER, floor + 1, oz + 1, Block.FENCE.id);
	}
}
