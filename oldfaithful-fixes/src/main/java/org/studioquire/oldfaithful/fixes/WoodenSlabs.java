package org.studioquire.oldfaithful.fixes;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * #9: every slab is one block of stone material whatever its kind (the kind is the block's data value), so wooden
 * slabs needed a pickaxe to drop and mined slowly by hand or axe. The game asks "can this player harvest this block,
 * and how fast" of the block alone, without the data value, so the fix answers at the places that know the position:
 * a wooden slab is harvestable as planks are, and mines at least as fast as planks would (a pickaxe keeps its stone
 * speed).
 */
public final class WoodenSlabs {
	private static final int WOOD = 2;

	/** In multiplayer, whether the server has this fix (ServerMarker). */
	public static volatile boolean serverAgrees;

	private WoodenSlabs() {
	}

	public static boolean isWooden(int blockId, int meta) {
		return (blockId == Block.SLAB.id || blockId == Block.DOUBLE_SLAB.id) && meta == WOOD;
	}

	public static boolean isWooden(World world, int x, int y, int z) {
		return isWooden(world.getBlockId(x, y, z), world.getBlockMeta(x, y, z));
	}

	/** The share of the block a tick of mining takes off: the game's, or planks' at the slab's hardness if more. */
	public static float hardness(Block block, PlayerEntity player, float vanilla, World world, int x, int y, int z) {
		if (!isWooden(world, x, y, z) || block.getHardness() <= 0) return vanilla;
		float asPlanks = Block.PLANKS.getHardness(player) * Block.PLANKS.getHardness() / block.getHardness();
		return Math.max(vanilla, asPlanks);
	}
}
