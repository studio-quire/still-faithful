package org.studioquire.oldfaithful.polish.mixin.leaves;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.item.LeavesBlockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.PlacedLeaves;

/**
 * #24: beta places leaves already marked to be checked for decay (data bit 8), so placed leaves with no log within four
 * blocks go on their next random tick. Placed leaves get the kept mark instead ({@link PlacedLeaves}).
 */
@Mixin(LeavesBlockItem.class)
abstract class LeavesBlockItemMixin {
	@ModifyReturnValue(method = "getPlacementMetadata", at = @At("RETURN"))
	private int oldfaithful$placedStay(int meta) {
		return PlacedLeaves.placed(meta);
	}
}
