package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.SeamCheck;

/** For SeamCheck: the camera's matrices, then the frame's colour and depth before the hand clears the depth. Idle unless asked. */
@Mixin(GameRenderer.class)
abstract class GameRendererSeamMixin {
	@Shadow
	private Minecraft client;

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void oldfaithful$seamsStart(float tickDelta, long time, CallbackInfo ci) {
		if (SeamCheck.ENABLED) SeamCheck.beforeFrame(client);
	}

	@Inject(method = "renderWorld", at = @At("RETURN"))
	private void oldfaithful$seamsMatrices(float tickDelta, int eye, CallbackInfo ci) {
		if (SeamCheck.ENABLED) SeamCheck.matrices();
	}

	@Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClear(I)V", ordinal = 1, remap = false))
	private void oldfaithful$seamsFrame(float tickDelta, long time, CallbackInfo ci) {
		if (SeamCheck.ENABLED) SeamCheck.frame(client);
	}
}
