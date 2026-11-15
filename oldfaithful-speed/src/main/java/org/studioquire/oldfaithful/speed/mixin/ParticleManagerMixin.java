package org.studioquire.oldfaithful.speed.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.ParticleList;

/** The particle layers as {@link ParticleList}s: the same particles in the same order, without the quadratic shifting. */
@Mixin(ParticleManager.class)
abstract class ParticleManagerMixin {
	@Shadow
	private List[] particles;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void oldfaithful$layers(World world, TextureManager textureManager, CallbackInfo ci) {
		for (int i = 0; i < particles.length; i++) {
			// Only a layer still vanilla's own, empty ArrayList: another mod's list is left alone.
			if (particles[i] != null && particles[i].getClass() == ArrayList.class && particles[i].isEmpty()) particles[i] = new ParticleList<>();
		}
	}
}
