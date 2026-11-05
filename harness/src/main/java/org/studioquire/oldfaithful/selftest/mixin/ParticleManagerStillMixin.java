package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lava pops and drips fall by chance: not in a scene compared pixel for pixel (`coreInertTest`). */
@Mixin(ParticleManager.class)
abstract class ParticleManagerStillMixin {
	@Inject(method = "addParticle", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$still(Particle particle, CallbackInfo ci) {
		if (Boolean.getBoolean("oldfaithful.selftest.sceneStill")) ci.cancel();
	}
}
