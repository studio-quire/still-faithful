package org.studioquire.oldfaithful.fixes.mixin.sky;

import net.minecraft.client.render.WorldRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #7: an open container screen ends its frame with GL lighting enabled, and the next frame draws the sky's dome
 * before anything turns lighting off, so the dome is lit by the light setup's ambient term only and reads grey. The
 * sky is meant to be unlit (renderSky turns lighting off itself, just after the dome), so turn it off first. In any
 * other frame lighting is already off here and nothing changes.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@Inject(method = "renderSky", at = @At("HEAD"))
	private void oldfaithful$unlitSky(float tickDelta, CallbackInfo ci) {
		GL11.glDisable(GL11.GL_LIGHTING);
	}
}
