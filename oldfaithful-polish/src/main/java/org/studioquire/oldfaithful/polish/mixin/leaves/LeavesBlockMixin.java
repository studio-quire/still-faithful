package org.studioquire.oldfaithful.polish.mixin.leaves;

import java.util.Random;

import net.minecraft.block.LeavesBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.PlacedLeaves;

/**
 * #24: leaves with the kept mark never decay. Breaking a log or leaves beside them still sets beta's check bit (8) on
 * them, which beta reads; here their tick does nothing, so the bit stays and the leaves stay.
 */
@Mixin(LeavesBlock.class)
abstract class LeavesBlockMixin {
	@Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$keptLeaves(World world, int x, int y, int z, Random random, CallbackInfo ci) {
		if (PlacedLeaves.isPlaced(world.getBlockMeta(x, y, z))) ci.cancel();
	}
}
