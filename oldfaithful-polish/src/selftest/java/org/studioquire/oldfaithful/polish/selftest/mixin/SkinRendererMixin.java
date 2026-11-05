package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.selftest.SkinCheck;

/**
 * For SkinCheck: the arms' idle sway follows the frame's time, so it's held still and pictures from two runs can be
 * compared; and each drawing of the figure on show is counted, so a picture waits until it's really in the frame.
 */
@Mixin(LivingEntityRenderer.class)
abstract class SkinRendererMixin {
	@Inject(method = "getHeadBob", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$noSway(LivingEntity entity, float tickDelta, CallbackInfoReturnable<Float> ci) {
		if (SkinCheck.still) ci.setReturnValue(0.0F);
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;DDDFF)V", at = @At("HEAD"))
	private void oldfaithful$drawn(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
		if (entity == SkinCheck.onShow) SkinCheck.drawn++;
	}
}
