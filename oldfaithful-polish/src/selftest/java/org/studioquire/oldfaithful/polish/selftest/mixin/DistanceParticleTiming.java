package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.selftest.DistanceCheck;

/**
 * For DistanceCheck: the game thread's time in drawing particles (both of a frame's passes, counted as one frame by the
 * first) and in moving them (a tick).
 */
@Mixin(ParticleManager.class)
abstract class DistanceParticleTiming {
	@Unique private long oldfaithful$start;

	@Inject(method = {"render", "renderLit", "removeDeadParticles"}, at = @At("HEAD"))
	private void oldfaithful$start(CallbackInfo ci) {
		oldfaithful$start = System.nanoTime();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void oldfaithful$drawn(Entity camera, float tickDelta, CallbackInfo ci) {
		if (!DistanceCheck.timing) return;
		DistanceCheck.particleRenderNs += System.nanoTime() - oldfaithful$start;
		DistanceCheck.particleRenderCalls++;
	}

	@Inject(method = "renderLit", at = @At("RETURN"))
	private void oldfaithful$drawnLit(Entity camera, float tickDelta, CallbackInfo ci) {
		if (DistanceCheck.timing) DistanceCheck.particleRenderNs += System.nanoTime() - oldfaithful$start;
	}

	@Inject(method = "removeDeadParticles", at = @At("RETURN"))
	private void oldfaithful$moved(CallbackInfo ci) {
		if (!DistanceCheck.timing) return;
		DistanceCheck.particleTickNs += System.nanoTime() - oldfaithful$start;
		DistanceCheck.particleTicks++;
	}
}
