package org.studioquire.oldfaithful.polish.mixin.watersources;

import net.minecraft.block.FlowingLiquidBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * #20, water: flowing water between two sources becomes a source when the block below is solid, or (meant) when it's
 * still water. Beta's second check reads the flowing block's own data instead of the block below's, which is never 0
 * there, so sources never formed over water. Here it reads the block below, as later versions do. This is the only
 * data read in onTick's own body (the levels go through getLiquidState).
 */
@Mixin(FlowingLiquidBlock.class)
abstract class FlowingLiquidBlockMixin {
	@ModifyArg(method = "onTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockMeta(III)I"), index = 1)
	private int oldfaithful$readBelow(int y) {
		return y - 1;
	}
}
