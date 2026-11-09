package org.studioquire.oldfaithful.polish.mixin.chestlids;

import net.minecraft.block.ChestBlock;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.Chests;

/** A double chest's top and bottom from the joined lid's halves ({@link org.studioquire.oldfaithful.polish.ChestLids}). */
@Mixin(ChestBlock.class)
abstract class ChestBlockMixin {
	@Inject(method = "getTextureId(Lnet/minecraft/world/BlockView;IIII)I", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$joinedLid(BlockView view, int x, int y, int z, int side, CallbackInfoReturnable<Integer> cir) {
		if (side != 0 && side != 1) return;
		int lid = Chests.lid(view, x, y, z, ((ChestBlock) (Object) this).id);
		if (lid >= 0) cir.setReturnValue(lid);
	}
}
