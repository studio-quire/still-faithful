package org.studioquire.oldfaithful.selftest.mixin;

import java.util.Random;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The spawn search on a fixed seed, as for the benchmark, so two worlds on one seed load the same chunks in the same order (`coreInertTest`). */
@Mixin(World.class)
abstract class WorldFixedSpawnMixin {
	private static final Long FIXED = Long.getLong("oldfaithful.selftest.fixedSpawn");

	@Shadow
	public Random random;

	@Inject(method = {"initializeSpawnPoint", "updateSpawnPosition"}, at = @At("HEAD"))
	private void oldfaithful$fixedSpawn(CallbackInfo ci) {
		if (FIXED != null) random.setSeed(FIXED);
	}
}
