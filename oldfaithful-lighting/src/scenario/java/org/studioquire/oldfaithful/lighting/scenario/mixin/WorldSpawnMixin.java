package org.studioquire.oldfaithful.lighting.scenario.mixin;

import java.util.Random;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The spawn search seeded (as the benchmark's), so every run of the test plays on the same terrain. */
@Mixin(World.class)
abstract class WorldSpawnMixin {
	@Shadow
	public Random random;

	@Inject(method = {"initializeSpawnPoint", "updateSpawnPosition"}, at = @At("HEAD"), require = 0)
	private void oldfaithful$fixedSpawnSearch(CallbackInfo ci) {
		random.setSeed(404);
	}
}
