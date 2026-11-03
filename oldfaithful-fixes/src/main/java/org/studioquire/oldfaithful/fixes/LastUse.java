package org.studioquire.oldfaithful.fixes;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * #8: breaking a block, the game wears the tool down and, if that was its last use, takes it out of the hand before
 * the block decides its drops, which read the hand: on the server whether the tool can harvest the block at all, and
 * in both games what leaves drop when cut with shears. The fix keeps the used-up tool in the hand until the block has
 * dropped, then removes it, as the game would have a moment earlier.
 */
public final class LastUse {
	private LastUse() {
	}

	/** Called at the end of the break: removes the used-up tool kept for the drops, if it's still in the hand. */
	public static void finish(PlayerEntity player, ItemStack kept) {
		if (player != null && kept != null && kept.count <= 0 && player.getHand() == kept) player.clearStackInHand();
	}
}
