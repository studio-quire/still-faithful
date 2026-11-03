package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.selftest.DuskCheck;

/** For DuskCheck: no new particles (torch smoke, lava pops) while pictures are compared. */
@Mixin(ParticleManager.class)
abstract class DuskParticleManagerMixin {
	@Inject(method = "addParticle", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$none(Particle particle, CallbackInfo ci) {
		if (DuskCheck.frozen) ci.cancel();
	}
}
