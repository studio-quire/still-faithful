package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
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
 * <p>
 * Pairs as later versions make them (blocks.chestPairs, the user's ask, 2026-11-08): a chest placed joins a chest
 * beside it only if that one faces the same way, stands to its left or right and isn't joined already, and never while
 * sneaking; any chest can go anywhere, beside a double chest too. So a chest joined that way says who to in its data:
 * {@link #PAIRED} + (facing - 2) * 2 + side, 6 to 13, the side 0 for a partner clockwise of its front (seen from above)
 * and 1 the other way, and two chests are a pair only if each says the other. Every other chest (data 0, a facing
 * alone) keeps beta's rule: joined to the first chest beside it that doesn't say, in the order beta's chest looks, so a
 * world's pairs stay as they were. A chest placed beside one of those without joining it says it's joined elsewhere (a
 * side with no partner), or beta's rule would join them. Drawing, the joined lid and opening all go by {@link #partner}.
 */
public final class Chests {
	static final int PAIRED = 6;
	/** Steps by facing value: 2 north (-z), 3 south (+z), 4 west (-x), 5 east (+x). */
	private static final int[] DX = {0, 0, 0, 0, -1, 1}, DZ = {0, 0, -1, 1, 0, 0};
	/** Beta's chest looks for a partner west, east, north, south (ChestBlock.onUse). */
	private static final int[] BETA_ORDER = {4, 5, 2, 3};
	/** The joined lid's halves in terrain.png ({@link ChestLids}): the west, east, north and south half of a pair. */
	public static final int LIDS = 250;

	private Chests() {
	}

	public static boolean isFacing(int meta) {
		return meta >= 2 && meta <= 5;
	}

	static boolean isPaired(int meta) {
		return meta >= PAIRED && meta < PAIRED + 8;
	}

	static int pairedFacing(int meta) {
		return 2 + (meta - PAIRED >> 1);
	}

	/** Where a paired chest's partner is, as a facing value. */
	static int pairedToward(int meta) {
		int clockwise = clockwise(pairedFacing(meta));
		return (meta - PAIRED & 1) == 0 ? clockwise : opposite(clockwise);
	}

	static int paired(int facing, int toward) {
		return PAIRED + (facing - 2) * 2 + (toward == clockwise(facing) ? 0 : 1);
	}

	/** Seen from above: north, east, south, west. */
	private static int clockwise(int facing) {
		switch (facing) {
			case 2: return 5;
			case 5: return 3;
			case 3: return 4;
			default: return 2;
		}
	}

	private static int opposite(int facing) {
		return facing ^ 1;
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

	/** The way to the chest this one is joined to, as a facing value, or 0 for none. */
	public static int partner(BlockView view, int x, int y, int z, int chestId) {
		return partner(view, x, y, z, chestId, Integer.MIN_VALUE, 0);
	}

	/** The same, with the chest at {@code skipX, skipZ} not counted for beta's rule: one being placed, not yet decided. */
	private static int partner(BlockView view, int x, int y, int z, int chestId, int skipX, int skipZ) {
		int own = view.getBlockMeta(x, y, z);
		if (isPaired(own)) {
			int d = pairedToward(own), px = x + DX[d], pz = z + DZ[d];
			if (view.getBlockId(px, y, pz) != chestId) return 0;
			int other = view.getBlockMeta(px, y, pz);
			return isPaired(other) && pairedFacing(other) == pairedFacing(own) && pairedToward(other) == opposite(d) ? d : 0;
		}
		for (int d : BETA_ORDER) {
			int px = x + DX[d], pz = z + DZ[d];
			if (px == skipX && pz == skipZ) continue;
			if (view.getBlockId(px, y, pz) == chestId && !isPaired(view.getBlockMeta(px, y, pz))) return d;
		}
		return 0;
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

	/** blocks.chestPairs: as later versions place a chest. Its data is still beta's 0 when this runs. */
	public static void placedPairing(World world, int x, int y, int z, LivingEntity placer, int chestId) {
		int facing = toward(placer.yaw), left = clockwise(facing);
		for (int d : BETA_ORDER) keepFront(world, x + DX[d], y, z + DZ[d], chestId, x, z);
		if (!placer.isSneaking()) {
			for (int d : new int[] {left, opposite(left)}) {
				int px = x + DX[d], pz = z + DZ[d];
				if (world.getBlockId(px, y, pz) != chestId || partner(world, px, y, pz, chestId, x, z) != 0 || facing(world, px, y, pz, x, z) != facing) continue;
				world.setBlockMeta(px, y, pz, paired(facing, opposite(d)));
				world.setBlockMeta(x, y, z, paired(facing, d));
				return;
			}
		}
		boolean besideBetas = false;
		for (int d : BETA_ORDER) {
			if (world.getBlockId(x + DX[d], y, z + DZ[d]) == chestId && !isPaired(world.getBlockMeta(x + DX[d], y, z + DZ[d]))) besideBetas = true;
		}
		if (!besideBetas) {
			world.setBlockMeta(x, y, z, facing);
			return;
		}
		// Joined to nobody, said so: toward a side whose chest isn't one of these pointing back, which would make a pair.
		int toward = pointsBack(world, x, y, z, left, facing, chestId) ? opposite(left) : left;
		world.setBlockMeta(x, y, z, paired(facing, toward));
	}

	/**
	 * A chest beside the one being placed, drawn by beta's rule from the blocks around it, would turn away from it: it
	 * keeps the front it's drawn with, written in its data (and its partner's), which draws the same.
	 */
	private static void keepFront(World world, int x, int y, int z, int chestId, int skipX, int skipZ) {
		if (world.getBlockId(x, y, z) != chestId) return;
		int own = world.getBlockMeta(x, y, z);
		if (isPaired(own)) return;
		int d = partner(world, x, y, z, chestId, skipX, skipZ);
		if (d == 0) {
			if (!isFacing(own)) world.setBlockMeta(x, y, z, betaFront(world, x, y, z, skipX, skipZ));
			return;
		}
		boolean alongX = DX[d] != 0;
		int px = x + DX[d], pz = z + DZ[d], other = world.getBlockMeta(px, y, pz);
		int front = isFacing(own) && across(own, alongX) ? own : isFacing(other) && across(other, alongX) ? other : betaPairFront(world, x, y, z, d, skipX, skipZ);
		if (own != front) world.setBlockMeta(x, y, z, front);
		if (other != front) world.setBlockMeta(px, y, pz, front);
	}

	private static boolean pointsBack(World world, int x, int y, int z, int d, int facing, int chestId) {
		int px = x + DX[d], pz = z + DZ[d], meta = world.getBlockMeta(px, y, pz);
		return world.getBlockId(px, y, pz) == chestId && isPaired(meta) && pairedFacing(meta) == facing && pairedToward(meta) == opposite(d);
	}

	/** Which way a chest faces as it's drawn alone, with the chest at {@code skipX, skipZ} taken as air. */
	private static int facing(BlockView view, int x, int y, int z, int skipX, int skipZ) {
		int own = view.getBlockMeta(x, y, z);
		if (isPaired(own)) return pairedFacing(own);
		if (isFacing(own)) return own;
		return betaFront(view, x, y, z, skipX, skipZ);
	}

	/** Beta's rule for a chest alone (ChestBlock.getTextureId): away from an opaque block, south when free, east-west over north-south. */
	private static int betaFront(BlockView view, int x, int y, int z, int skipX, int skipZ) {
		boolean north = opaque(view, x, y, z - 1, skipX, skipZ), south = opaque(view, x, y, z + 1, skipX, skipZ);
		boolean west = opaque(view, x - 1, y, z, skipX, skipZ), east = opaque(view, x + 1, y, z, skipX, skipZ);
		int front = 3;
		if (south && !north) front = 2;
		if (west && !east) front = 5;
		if (east && !west) front = 4;
		return front;
	}

	/** Beta's rule for a pair's front: away from the opaque side of either half, south (along x) or east (along z) when free. */
	private static int betaPairFront(BlockView view, int x, int y, int z, int d, int skipX, int skipZ) {
		int px = x + DX[d], pz = z + DZ[d];
		if (DX[d] != 0) {
			boolean north = opaque(view, x, y, z - 1, skipX, skipZ) || opaque(view, px, y, pz - 1, skipX, skipZ);
			boolean south = opaque(view, x, y, z + 1, skipX, skipZ) || opaque(view, px, y, pz + 1, skipX, skipZ);
			return south && !north ? 2 : 3;
		}
		boolean west = opaque(view, x - 1, y, z, skipX, skipZ) || opaque(view, px - 1, y, pz, skipX, skipZ);
		boolean east = opaque(view, x + 1, y, z, skipX, skipZ) || opaque(view, px + 1, y, pz, skipX, skipZ);
		return east && !west ? 4 : 5;
	}

	private static boolean opaque(BlockView view, int x, int y, int z) {
		return Block.BLOCKS_OPAQUE[view.getBlockId(x, y, z)];
	}

	private static boolean opaque(BlockView view, int x, int y, int z, int skipX, int skipZ) {
		return !(x == skipX && z == skipZ) && opaque(view, x, y, z);
	}

	/** Whether a facing points across a pair lying along x (alongX) or along z. */
	private static boolean across(int facing, boolean alongX) {
		return alongX ? facing == 2 || facing == 3 : facing == 4 || facing == 5;
	}

	/**
	 * The texture of a chest's side drawn from its facing and its partner, mirroring beta's ChestBlock.getTextureId; -1
	 * where beta's own rule gives the same (the top and bottom, a chest of beta's alone or beside only its partner).
	 */
	public static int texture(BlockView view, int x, int y, int z, int side, int chestId, int tex) {
		if (side == 0 || side == 1) return -1;
		int own = view.getBlockMeta(x, y, z);
		int d = partner(view, x, y, z, chestId);
		if (d == 0) {
			int front = isPaired(own) ? pairedFacing(own) : isFacing(own) ? own : besideChest(view, x, y, z, chestId, 0) ? betaFront(view, x, y, z, Integer.MIN_VALUE, 0) : -1;
			if (front < 0) return -1;
			return side == front ? tex + 1 : tex;
		}
		boolean alongX = DX[d] != 0;
		int partnerMeta = view.getBlockMeta(x + DX[d], y, z + DZ[d]);
		int front = isPaired(own) ? pairedFacing(own) : isFacing(own) && across(own, alongX) ? own : isFacing(partnerMeta) && across(partnerMeta, alongX) ? partnerMeta : -1;
		if (front < 0) {
			if (!besideChest(view, x, y, z, chestId, d) && !besideChest(view, x + DX[d], y, z + DZ[d], chestId, opposite(d))) return -1;
			front = betaPairFront(view, x, y, z, d, Integer.MIN_VALUE, 0);
		}
		if (alongX) {
			if (side == 4 || side == 5) return tex;
			int half = d == 4 ? -1 : 0;
			if (side == 3) half = -1 - half;
			return (side == front ? tex + 16 : tex + 32) + half;
		}
		if (side == 2 || side == 3) return tex;
		int half = d == 2 ? -1 : 0;
		if (side == 4) half = -1 - half;
		return (side == front ? tex + 16 : tex + 32) + half;
	}

	/** Whether there's a chest beside this one other than the way {@code except} (0 for none). */
	private static boolean besideChest(BlockView view, int x, int y, int z, int chestId, int except) {
		for (int d : BETA_ORDER) {
			if (d != except && view.getBlockId(x + DX[d], y, z + DZ[d]) == chestId) return true;
		}
		return false;
	}

	/** render.chestLids: a pair's top and bottom drawn as one lid, from the halves {@link ChestLids} adds; -1 for a chest alone. */
	public static int lid(BlockView view, int x, int y, int z, int chestId) {
		switch (partner(view, x, y, z, chestId)) {
			case 5: return LIDS;
			case 4: return LIDS + 1;
			case 3: return LIDS + 2;
			case 2: return LIDS + 3;
			default: return -1;
		}
	}

	/**
	 * blocks.chestPairs: a chest opened with its partner only, the half further west or north on top as in beta; shut, as
	 * in beta, while a solid block stands on either half.
	 */
	public static boolean use(World world, int x, int y, int z, PlayerEntity player, int chestId) {
		Inventory inventory = (ChestBlockEntity) world.getBlockEntity(x, y, z);
		if (world.shouldSuffocate(x, y + 1, z)) return true;
		int d = partner(world, x, y, z, chestId);
		if (d != 0) {
			int px = x + DX[d], pz = z + DZ[d];
			if (world.shouldSuffocate(px, y + 1, pz)) return true;
			ChestBlockEntity other = (ChestBlockEntity) world.getBlockEntity(px, y, pz);
			inventory = d == 4 || d == 2 ? new DoubleInventory("Large chest", other, inventory) : new DoubleInventory("Large chest", inventory, other);
		}
		if (world.isRemote) return true;
		player.openChestScreen(inventory);
		return true;
	}
}
