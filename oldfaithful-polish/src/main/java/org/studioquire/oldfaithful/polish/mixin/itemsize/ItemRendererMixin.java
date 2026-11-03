package org.studioquire.oldfaithful.polish.mixin.itemsize;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #34: a dropped block drawn in 3D is scaled to a quarter, except one that isn't a full cube (stairs, fences, cactus,
 * pressure plates, buttons, trapdoors...), which beta scales to a half: twice the size of every other dropped block.
 * Later versions draw them all at a quarter. Here the size test always answers "full cube"; slabs and pistons, which
 * beta already drew at a quarter, are unchanged, and so is what's drawn.
 */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
	@WrapOperation(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;isFullCube()Z"))
	private boolean oldfaithful$quarterSize(Block block, Operation<Boolean> original) {
		return true;
	}
}
