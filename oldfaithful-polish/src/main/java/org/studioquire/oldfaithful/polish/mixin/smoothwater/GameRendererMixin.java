package org.studioquire.oldfaithful.polish.mixin.smoothwater;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.SmoothWater;

/**
 * Smooth water in Fast graphics: beta draws the translucent layer (water, ice, portals) with colours blended across each
 * face only in Fancy; in Fast it draws it with GL's flat shading, each triangle in one corner's colour, which turns
 * per-corner light into triangles. With Smooth Lighting on, that pass is drawn blended in Fast too, as the solid layer
 * already is. (Ice, which beta lights per corner, was drawn in triangles there; now it's blended as in Fancy.)
 *
 * <p>Also where a change of the player's choice on the settings screen shows: the chunks in view are rebuilt before the
 * next frame, as beta rebuilds them when Smooth Lighting is switched.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Shadow
	private Minecraft client;

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void oldfaithful$redraw(float tickDelta, long time, CallbackInfo ci) {
		if (!SmoothWater.redraw) return;
		SmoothWater.redraw = false;
		// Without Smooth Lighting water looks the same whatever the choice; switching it on rebuilds everything anyway.
		if (client.options.ao) client.worldRenderer.reload();
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/entity/LivingEntity;ID)I"))
	private int oldfaithful$blendTranslucent(WorldRenderer renderer, LivingEntity camera, int layer, double tickDelta, Operation<Integer> original) {
		if (layer != 1 || client.options.fancyGraphics || !SmoothWater.blends()) return original.call(renderer, camera, layer, tickDelta);
		GL11.glShadeModel(GL11.GL_SMOOTH);
		try {
			return original.call(renderer, camera, layer, tickDelta);
		} finally {
			GL11.glShadeModel(GL11.GL_FLAT);
		}
	}
}
