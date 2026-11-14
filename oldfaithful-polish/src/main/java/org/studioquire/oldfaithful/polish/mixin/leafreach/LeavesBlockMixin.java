package org.studioquire.oldfaithful.polish.mixin.leafreach;

import net.minecraft.block.LeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.studioquire.oldfaithful.polish.LeafReach;

/**
 * Leaf Reach: beta's decay check has its 4 twice, the cube it floods (and asks to be loaded, one wider) and the flood's
 * steps; both become {@link LeafReach#reach()}, so a leaf stays within 6 steps of a log through leaves. Beta's own code
 * runs otherwise, so Faithful Core's reading of any mod's leaves and logs as beta's, and placed leaves that never decay
 * (Placed Leaves, which returns before this), go on as they were.
 */
@Mixin(LeavesBlock.class)
abstract class LeavesBlockMixin {
	@ModifyConstant(method = "onTick", constant = @Constant(intValue = 4), require = 2)
	private int oldfaithful$reach(int beta) {
		return LeafReach.reach();
	}
}
