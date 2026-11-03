package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.SceneShot;

/** For SceneShot: the frame read back once it's drawn, before the buffers swap. Does nothing unless a shot is asked for. */
@Mixin(GameRenderer.class)
abstract class GameRendererSceneMixin {
	@Shadow
	private Minecraft client;

	@Inject(method = "onFrameUpdate", at = @At("RETURN"))
	private void oldfaithful$sceneShot(float tickDelta, CallbackInfo ci) {
		if (SceneShot.ENABLED) SceneShot.frame(client);
	}
}
