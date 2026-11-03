package org.studioquire.oldfaithful.fixes;

import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * #46: pick block (the middle button) selects the hotbar slot holding what the crosshair points at. Beta looked for the
 * block's own id, which is the item's only for some blocks, so redstone, repeaters, doors, signs, wheat, beds, cake and
 * sugar cane found nothing; slab, wool and log kinds weren't told apart; and pointing at a mob read the empty block
 * position 0, 0, 0 (bedrock, which beta maps to stone). Beta's own mappings stay: grass picks dirt, a double slab a
 * slab, bedrock stone.
 */
public final class PickBlock {
	private PickBlock() {
	}

	/** The item id a block is picked as, or 0 for none. */
	public static int itemFor(int blockId, int meta) {
		if (blockId <= 0 || blockId >= Block.BLOCKS.length || Block.BLOCKS[blockId] == null) return 0;
		if (blockId == Block.GRASS_BLOCK.id || blockId == Block.FARMLAND.id) return Block.DIRT.id;
		if (blockId == Block.DOUBLE_SLAB.id) return Block.SLAB.id;
		if (blockId == Block.BEDROCK.id) return Block.STONE.id;
		if (blockId == Block.REDSTONE_WIRE.id) return Item.REDSTONE.id;
		if (blockId == Block.REPEATER.id || blockId == Block.POWERED_REPEATER.id) return Item.REPEATER.id;
		if (blockId == Block.DOOR.id) return Item.WOODEN_DOOR.id;
		if (blockId == Block.IRON_DOOR.id) return Item.IRON_DOOR.id;
		if (blockId == Block.SIGN.id || blockId == Block.WALL_SIGN.id) return Item.SIGN.id;
		if (blockId == Block.WHEAT.id) return Item.SEEDS.id;
		if (blockId == Block.BED.id) return Item.BED.id;
		if (blockId == Block.CAKE.id) return Item.CAKE.id;
		if (blockId == Block.SUGAR_CANE.id) return Item.SUGAR_CANE.id;
		if (blockId == Block.REDSTONE_TORCH.id) return Block.LIT_REDSTONE_TORCH.id;
		if (blockId == Block.LIT_FURNACE.id) return Block.FURNACE.id;
		if (blockId == Block.LIT_REDSTONE_ORE.id) return Block.REDSTONE_ORE.id;
		if (blockId == Block.PISTON_HEAD.id) return (meta & 8) != 0 ? Block.STICKY_PISTON.id : Block.PISTON.id;
		return blockId;
	}

	/**
	 * The hotbar slot to select: one holding the item of the same kind if the item has kinds (wool colours, slabs,
	 * logs, saplings), otherwise the first holding the item at all; -1 for none.
	 */
	public static int hotbarSlot(PlayerInventory inventory, int itemId, int kind) {
		int any = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inventory.main[i];
			if (s == null || s.itemId != itemId) continue;
			if (!s.hasSubtypes() || s.getDamage() == kind) return i;
			if (any < 0) any = i;
		}
		return any;
	}
}
