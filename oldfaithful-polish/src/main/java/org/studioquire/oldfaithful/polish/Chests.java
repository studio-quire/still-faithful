package org.studioquire.oldfaithful.polish;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * #69: chests that face the player who placed them. Beta stores no facing for a chest (its data is always 0) and works
 * out its front from the blocks around it every time it's drawn (ChestBlock.getTextureId): away from an opaque block
 * behind it, south when it stands free, and the east-west test overrides the north-south one, so in a corner it turns
 * sideways. Here a placed chest keeps a facing in its data, as furnaces do (2 north, 3 south, 4 west, 5 east), and is
 * drawn from it; data 0, every chest placed before, keeps beta's rule, so nothing in an existing world turns. A vanilla
 * game ignores a chest's data and draws such a chest by beta's rule; the data is dropped with the block (Block's
 * dropped data is 0), so the item stacks as before.
 * <p>
 * A double chest faces across its length. Placing a chest beside one that has a facing joins it (as later versions do);
 * otherwise both halves take the placer's, turned to the nearer side across the pair if the player looked along it.
 */
public final class Chests {
	private Chests() {
	}

	public static boolean isFacing(int meta) {
		return meta >= 2 && meta <= 5;
	}

	/** The face toward the player, from where they look (yaw 0 looks south, +z): furnace's rule. */
	static int toward(float yaw) {
		switch ((int) Math.floor(yaw * 4.0F / 360.0F + 0.5) & 3) {
			case 0: return 2;
			case 1: return 5;
			case 2: return 3;
			default: return 4;
		}
	}

	public static void placed(World world, int x, int y, int z, LivingEntity placer, int chestId) {
		int facing = toward(placer.yaw);
		double rad = placer.yaw * Math.PI / 180.0;
		int px = x, pz = z;
		boolean alongX = false, paired = true;
		if (world.getBlockId(x - 1, y, z) == chestId) { px = x - 1; alongX = true; }
		else if (world.getBlockId(x + 1, y, z) == chestId) { px = x + 1; alongX = true; }
		else if (world.getBlockId(x, y, z - 1) == chestId) pz = z - 1;
		else if (world.getBlockId(x, y, z + 1) == chestId) pz = z + 1;
		else paired = false;
		if (paired) {
			int partner = world.getBlockMeta(px, y, pz);
			if (isFacing(partner) && across(partner, alongX)) {
				facing = partner;
			} else if (!across(facing, alongX)) {
				// Looking along the pair: the side across it that the player is on.
				facing = alongX ? (Math.cos(rad) > 0 ? 2 : 3) : (-Math.sin(rad) > 0 ? 4 : 5);
			}
			if (partner != facing) world.setBlockMeta(px, y, pz, facing);
		}
		world.setBlockMeta(x, y, z, facing);
	}

	/** Whether a facing points across a pair lying along x (alongX) or along z. */
	private static boolean across(int facing, boolean alongX) {
		return alongX ? facing == 2 || facing == 3 : facing == 4 || facing == 5;
	}

	/**
	 * The texture of a chest's side drawn from its facing, mirroring beta's ChestBlock.getTextureId with the front
	 * taken from the data; -1 where beta's own rule applies (data 0 on both halves, or the top and bottom).
	 */
	public static int texture(BlockView view, int x, int y, int z, int side, int chestId, int tex) {
		if (side == 0 || side == 1) return -1;
		int north = view.getBlockId(x, y, z - 1), south = view.getBlockId(x, y, z + 1);
		int west = view.getBlockId(x - 1, y, z), east = view.getBlockId(x + 1, y, z);
		int own = view.getBlockMeta(x, y, z);
		if (north != chestId && south != chestId) {
			if (west != chestId && east != chestId) {
				if (!isFacing(own)) return -1;
				return side == own ? tex + 1 : tex;
			}
			if (side == 4 || side == 5) return tex;
			int front = pairFront(own, view.getBlockMeta(west == chestId ? x - 1 : x + 1, y, z), true);
			if (front < 0) return -1;
			int half = west == chestId ? -1 : 0;
			if (side == 3) half = -1 - half;
			return (side == front ? tex + 16 : tex + 32) + half;
		}
		if (side == 2 || side == 3) return tex;
		int front = pairFront(own, view.getBlockMeta(x, y, north == chestId ? z - 1 : z + 1), false);
		if (front < 0) return -1;
		int half = north == chestId ? -1 : 0;
		if (side == 4) half = -1 - half;
		return (side == front ? tex + 16 : tex + 32) + half;
	}

	private static int pairFront(int own, int partner, boolean alongX) {
		if (isFacing(own) && across(own, alongX)) return own;
		if (isFacing(partner) && across(partner, alongX)) return partner;
		return -1;
	}
}
