package org.studioquire.oldfaithful.polish.mixin.leafreach;

import net.minecraft.block.LogBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.studioquire.oldfaithful.polish.LeafReach;

/**
 * Leaf Reach: a broken log marks the leaves within {@link LeafReach#reach()} each way to look for another log, not
 * beta's 4, or leaves 5 and 6 from a log that's gone would never look and hang on forever.
 */
@Mixin(LogBlock.class)
abstract class LogBlockMixin {
	@ModifyConstant(method = "onBreak", constant = @Constant(intValue = 4))
	private int oldfaithful$reach(int beta) {
		return LeafReach.reach();
	}
}
