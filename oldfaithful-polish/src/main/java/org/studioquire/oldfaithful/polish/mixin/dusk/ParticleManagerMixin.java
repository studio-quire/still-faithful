package org.studioquire.oldfaithful.polish.mixin.dusk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.studioquire.oldfaithful.polish.DuskLight;

/** #6: particles lit at the same darkness as the terrain (DuskLight.entityScope). */
@Mixin(ParticleManager.class)
abstract class ParticleManagerMixin {
	@WrapMethod(method = "render")
	private void oldfaithful$particles(Entity camera, float tickDelta, Operation<Void> original) {
		DuskLight.entityScope++;
		try {
			original.call(camera, tickDelta);
		} finally {
			DuskLight.entityScope--;
		}
	}

	@WrapMethod(method = "renderLit")
	private void oldfaithful$litParticles(Entity camera, float tickDelta, Operation<Void> original) {
		DuskLight.entityScope++;
		try {
			original.call(camera, tickDelta);
		} finally {
			DuskLight.entityScope--;
		}
	}
}
