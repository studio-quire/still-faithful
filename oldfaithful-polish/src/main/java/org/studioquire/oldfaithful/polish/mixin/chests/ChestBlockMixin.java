package org.studioquire.oldfaithful.polish.mixin.chests;

import net.minecraft.block.ChestBlock;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.Chests;

/** #69: a chest with a facing in its data is drawn from it; data 0 keeps beta's rule from the blocks around. */
@Mixin(ChestBlock.class)
abstract class ChestBlockMixin {
	@Inject(method = "getTextureId(Lnet/minecraft/world/BlockView;IIII)I", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$drawnFacing(BlockView view, int x, int y, int z, int side, CallbackInfoReturnable<Integer> cir) {
		ChestBlock self = (ChestBlock) (Object) this;
		int texture = Chests.texture(view, x, y, z, side, self.id, self.textureId);
		if (texture >= 0) cir.setReturnValue(texture);
	}
}
