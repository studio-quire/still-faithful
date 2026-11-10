package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.render.Culler;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.selftest.DistanceCheck;

/** For DistanceCheck: the game thread's time in drawing things (entities), a frame at a time. */
@Mixin(WorldRenderer.class)
abstract class DistanceEntityTiming {
	@Unique private long oldfaithful$start;

	@Inject(method = "renderEntities", at = @At("HEAD"))
	private void oldfaithful$start(Vec3d camera, Culler culler, float tickDelta, CallbackInfo ci) {
		oldfaithful$start = System.nanoTime();
	}

	@Inject(method = "renderEntities", at = @At("RETURN"))
	private void oldfaithful$end(Vec3d camera, Culler culler, float tickDelta, CallbackInfo ci) {
		if (!DistanceCheck.timing) return;
		DistanceCheck.entityNs += System.nanoTime() - oldfaithful$start;
		DistanceCheck.entityCalls++;
	}
}
