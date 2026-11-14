package org.studioquire.oldfaithful.fixes.mixin.leafedge;

import java.util.Random;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.block.LeavesBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #85: a leaf marked to look for a log (data bit 8) whose surroundings aren't all loaded decides nothing. Beta's
 * {@code onTick} skips filling its array from the blocks round the leaf when its region test fails, then keeps or drops
 * the leaf by the array's middle anyway, which is whatever the last check anywhere left there (one array for every
 * leaf). Here the leaf stays as it is, mark and all, and a later random tick decides once the land round it is loaded.
 * <p>
 * Keyed on the method's own region test, so it holds for whatever size another mod gives that test (Still Faithful's
 * Leaf Reach widens it to 7 each way) and for every leaf that runs beta's check (Faithful Core's {@code Leaves}).
 */
@Mixin(LeavesBlock.class)
abstract class LeavesBlockMixin {
	@ModifyExpressionValue(method = "onTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRegionLoaded(IIIIII)Z"))
	private boolean oldfaithful$noteUnloaded(boolean loaded, @Share("unloaded") LocalBooleanRef unloaded) {
		unloaded.set(!loaded);
		return loaded;
	}

	// Keeping (clearing the mark) and dropping are the check's only two outcomes.
	@Inject(method = "onTick", cancellable = true, require = 2, at = {
			@At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockMetaWithoutNotifyingNeighbors(IIII)Z"),
			@At(value = "INVOKE", target = "Lnet/minecraft/block/LeavesBlock;breakLeaves(Lnet/minecraft/world/World;III)V")})
	private void oldfaithful$decideNothing(World world, int x, int y, int z, Random random, CallbackInfo ci,
			@Share("unloaded") LocalBooleanRef unloaded) {
		if (unloaded.get()) ci.cancel();
	}
}
