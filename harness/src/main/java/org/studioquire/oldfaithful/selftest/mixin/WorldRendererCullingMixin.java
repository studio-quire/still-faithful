package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Culling;

/** For Culling: the probe placed as entities are about to be drawn, with the frame's culler. Idle unless a scene is set. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererCullingMixin {
	@Shadow
	private Minecraft client;

	@Inject(method = "renderEntities", at = @At("HEAD"))
	private void oldfaithful$cullingProbe(Vec3d camera, Culler culler, float tickDelta, CallbackInfo ci) {
		if (Culling.scene != null) Culling.entities(client, culler, tickDelta);
	}
}
