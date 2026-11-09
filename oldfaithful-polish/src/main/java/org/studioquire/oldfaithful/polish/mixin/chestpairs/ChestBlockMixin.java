package org.studioquire.oldfaithful.polish.mixin.chestpairs;

import net.minecraft.block.ChestBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.Chests;

/** Chests pair as later versions pair them ({@link Chests}): a chest goes anywhere, and opens with its own partner only. Placing: chests.BlockMixin. */
@Mixin(ChestBlock.class)
abstract class ChestBlockMixin {
	@Inject(method = "canPlaceAt", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$anywhere(World world, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}

	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$openPair(World world, int x, int y, int z, PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(Chests.use(world, x, y, z, player, ((ChestBlock) (Object) this).id));
	}
}
