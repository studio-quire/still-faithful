package org.studioquire.oldfaithful.selftest.mixin;

import java.util.Random;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Bench;

/**
 * b1.7.3 searches for the spawn point with the world's unseeded Random, so each new world on the same seed puts the
 * player somewhere else, with different terrain to load. For the benchmark the search starts from a fixed seed, so
 * every run stands in the same place.
 */
@Mixin(World.class)
abstract class WorldSpawnBenchMixin {
	@Shadow
	public Random random;

	@Inject(method = {"initializeSpawnPoint", "updateSpawnPosition"}, at = @At("HEAD"))
	private void oldfaithful$fixedSpawnSearch(CallbackInfo ci) {
		if (Bench.ENABLED) random.setSeed(Bench.SEED);
	}
}
