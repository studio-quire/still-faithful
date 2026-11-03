package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.OptionsCheck;

/** The projection the world was drawn with, and the one the hand is drawn with, for OptionsCheck's FOV check. */
@Mixin(GameRenderer.class)
abstract class GameRendererFovProbeMixin {
	@Inject(method = "renderWorld", at = @At("TAIL"))
	private void oldfaithful$worldProjection(float tickDelta, int eye, CallbackInfo ci) {
		if (OptionsCheck.probing) OptionsCheck.worldProjected();
	}

	@Inject(method = "renderFirstPersonHand", at = @At("HEAD"))
	private void oldfaithful$handProjection(float tickDelta, int eye, CallbackInfo ci) {
		if (OptionsCheck.probing) OptionsCheck.handProjected();
	}
}
