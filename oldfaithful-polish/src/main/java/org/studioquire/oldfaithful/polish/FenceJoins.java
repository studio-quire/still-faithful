package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.CobwebBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.world.BlockView;

/**
 * Fences that join what later versions join them to. Beta's renderFence joins a fence only to a block of its own id,
 * so beside a wall it stands apart. From 1.13 a fence joins fences of its kind, fence gates, and any block whose side
 * toward it is a whole, solid face (its collision shape covers that face), except leaves, pumpkins, jack-o'-lanterns,
 * melons, barriers and shulker boxes; 1.0 to 1.12 joined only opaque whole cubes, so not glass.
 * <p>
 * Beta's own blocks map onto that by what they are, so a mod's block that subclasses one follows it: a fence joins
 * <ul>
 * <li>a FenceBlock of its material (wooden fences join wooden fences in later versions, whatever the wood);</li>
 * <li>no block whose material lets things through (air, water, lava, plants, torches, rails, snow layers, fire,
 * portals), and no leaves, pumpkins or jack-o'-lanterns (1.13's exceptions);</li>
 * <li>any block drawn as a whole cube: stone, glass, ice, glowstone, soul sand (whose side 1.13 counts as whole),
 * spawners, TNT, and beta's chest, a whole cube here where 1.13's is inset;</li>
 * <li>stairs at their back, the one whole side of beta's stairs (data 0 to 3, the high half to the east, west, south,
 * north);</li>
 * <li>any other block by its bounds as beta sets them for its state ({@code updateBoundingBox}, what its outline and
 * most collision read): where they cover the side toward the fence. So a door's flat side, an open trapdoor's, a
 * piston's back when it's out and its head's face; not a slab, a bed, farmland (15/16 high), cake, a sign or a pressure
 * plate. Cactus and cobwebs keep a whole block's bounds while being neither (cactus is inset, a cobweb has no
 * collision), so they're named, as is a block a piston is moving (it joins again where it lands).</li>
 * </ul>
 * What's joined is drawn (the renderFence mixin), and with Fence Shape (#30) it's also the arms aimed at, outlined and
 * bumped into ({@link Fences}). Every side reads the same blocks the same way, so wherever a client has this change its
 * arms are at least the server's, and a server's replay of its moves comes out as the client's (as for #30's thin
 * fences).
 */
public final class FenceJoins {
	public static final String KEY = "blocks.fenceJoins";
	/** Whether this change is on: a switch applies after a restart, so it's read once. */
	static final boolean ON = PolishConfig.applied(KEY);

	private FenceJoins() {
	}

	/**
	 * Whether the fence block {@code fence} at x y z reaches toward the block at x + dx, z + dz (dx and dz one of the
	 * four sides): a block of its own id, as in beta, or with this change on, what later versions join.
	 */
	public static boolean joins(BlockView view, Block fence, int x, int y, int z, int dx, int dz) {
		int id = view.getBlockId(x + dx, y, z + dz);
		return id == fence.id || ON && joinsOther(view, fence, id, x + dx, y, z + dz, -dx, -dz);
	}

	/** Whether the block {@code id} at x y z, not the fence's own id, is joined at its side toward sx, sz (the fence's). */
	static boolean joinsOther(BlockView view, Block fence, int id, int x, int y, int z, int sx, int sz) {
		Block b = id > 0 ? Block.BLOCKS[id] : null;
		if (b == null) return false;
		if (b instanceof FenceBlock) return b.material == fence.material;
		if (!b.material.blocksMovement() || b instanceof LeavesBlock || b instanceof PumpkinBlock) return false;
		if (b.isFullCube()) return true;
		if (b instanceof StairsBlock) return stairsBack(view.getBlockMeta(x, y, z), sx, sz);
		if (b instanceof CactusBlock || b instanceof CobwebBlock || b instanceof PistonExtensionBlock) return false;
		return wholeSide(b, view, x, y, z, sx, sz);
	}

	/** Beta's stairs (renderStairs, addIntersectingBoundingBox): data 0 high to the east, 1 west, 2 south, 3 north. */
	static boolean stairsBack(int meta, int sx, int sz) {
		switch (meta) {
			case 0:
				return sx == 1;
			case 1:
				return sx == -1;
			case 2:
				return sz == 1;
			case 3:
				return sz == -1;
			default:
				return false;
		}
	}

	/** Whether the block's bounds for its state at x y z cover its side toward sx, sz; its bounds are put back after. */
	private static boolean wholeSide(Block b, BlockView view, int x, int y, int z, int sx, int sz) {
		double minX = b.minX, minY = b.minY, minZ = b.minZ, maxX = b.maxX, maxY = b.maxY, maxZ = b.maxZ;
		try {
			b.updateBoundingBox(view, x, y, z);
			if (b.minY > 0 || b.maxY < 1) return false;
			if (sx != 0) return (sx > 0 ? b.maxX >= 1 : b.minX <= 0) && b.minZ <= 0 && b.maxZ >= 1;
			return (sz > 0 ? b.maxZ >= 1 : b.minZ <= 0) && b.minX <= 0 && b.maxX >= 1;
		} finally {
			b.minX = minX;
			b.minY = minY;
			b.minZ = minZ;
			b.maxX = maxX;
			b.maxY = maxY;
			b.maxZ = maxZ;
		}
	}
}
