package org.studioquire.oldfaithful.saves.scenario.mixin;

import net.minecraft.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.saves.scenario.Scenario;

/** Test-only (#78): a new overworld's spawn search, inside the real server's ServerWorld constructor, starts with a spring's water reaching a plant. */
@Mixin(World.class)
abstract class SpawnSearchMixin {
	@Inject(method = "initializeSpawnPoint", at = @At("HEAD"))
	private void oldfaithful$springOverPlant(CallbackInfo ci) {
		if ((Object) this instanceof ServerWorld) Scenario.beingBuilt((ServerWorld) (Object) this);
	}
}
