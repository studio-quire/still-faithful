package org.studioquire.oldfaithful.polish.scenario.mixin;

import net.minecraft.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.scenario.CartScenario;

@Mixin(World.class)
abstract class WorldTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void oldfaithful$scenario(CallbackInfo ci) {
		if ((Object) this instanceof ServerWorld) CartScenario.tick((ServerWorld) (Object) this);
	}
}
