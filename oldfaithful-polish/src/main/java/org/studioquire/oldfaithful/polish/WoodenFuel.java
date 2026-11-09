package org.studioquire.oldfaithful.polish;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Beta's furnace burns any block of wood material for 300 ticks (logs, planks, chests, fences, stairs, trapdoors...),
 * sticks and saplings for 100, and no other wooden thing. These are the rest, at the times later versions give them
 * (a smelt is 200 ticks): where beta and later versions both burn something they agree, so this extends beta's own
 * table. Beta's wooden slab (slab data 2) is stone material, mined with a pickaxe and never caught by fire; later
 * versions kept it as the petrified oak slab, which doesn't burn. It's still called a wooden slab and made of planks,
 * so it burns here, as later versions' wooden slabs do.
 */
public final class WoodenFuel {
	private WoodenFuel() {
	}

	/** Ticks of burning, or 0 for anything beta's own table already decides. */
	public static int ticks(ItemStack stack) {
		int id = stack.itemId;
		if (id == Item.BOAT.id) return 1200;
		if (id == Item.BOW.id || id == Item.FISHING_ROD.id || id == Block.LADDER.id) return 300;
		if (id == Item.WOODEN_SWORD.id || id == Item.WOODEN_SHOVEL.id || id == Item.WOODEN_PICKAXE.id || id == Item.WOODEN_AXE.id
				|| id == Item.WOODEN_HOE.id || id == Item.SIGN.id || id == Item.WOODEN_DOOR.id) return 200;
		if (id == Block.SLAB.id && stack.getDamage() == 2) return 150;
		if (id == Item.BOWL.id) return 100;
		return 0;
	}
}
