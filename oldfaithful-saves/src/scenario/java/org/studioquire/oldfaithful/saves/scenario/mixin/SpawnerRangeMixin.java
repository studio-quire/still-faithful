package org.studioquire.oldfaithful.saves.scenario.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.saves.scenario.Scenario;

/** Test-only (#65): the spawner the test names acts as if a player stood beside it, so it ticks fully with no one there. */
@Mixin(MobSpawnerBlockEntity.class)
abstract class SpawnerRangeMixin {
	@Inject(method = "isPlayerInRange", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$playerBeside(CallbackInfoReturnable<Boolean> cir) {
		if (Scenario.isTestSpawner((BlockEntity) (Object) this)) cir.setReturnValue(true);
	}
}
