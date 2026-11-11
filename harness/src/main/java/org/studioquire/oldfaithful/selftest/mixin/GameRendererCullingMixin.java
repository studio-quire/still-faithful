package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Culling;

/** For Culling: the camera placed as a frame starts, and the frame read once the world is drawn, before the hand clears the depth. Idle unless a scene is set. */
@Mixin(GameRenderer.class)
abstract class GameRendererCullingMixin {
	@Shadow
	private Minecraft client;

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void oldfaithful$cullingStart(float tickDelta, long time, CallbackInfo ci) {
		if (Culling.scene != null) Culling.frameStart(client);
	}

	@Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glClear(I)V", ordinal = 1, remap = false))
	private void oldfaithful$cullingDrawn(float tickDelta, long time, CallbackInfo ci) {
		if (Culling.scene != null) Culling.drawn(client);
	}
}
