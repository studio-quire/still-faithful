package org.studioquire.oldfaithful.fixes.scenario.mixin;

import net.minecraft.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.scenario.Scenario;
import org.studioquire.oldfaithful.fixes.scenario.SquidWatch;
import org.studioquire.oldfaithful.fixes.scenario.TickCensus;
import org.studioquire.oldfaithful.fixes.scenario.WorldFixes;

@Mixin(World.class)
abstract class WorldTickMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void oldfaithful$scenario(CallbackInfo ci) {
		if (!((Object) this instanceof ServerWorld)) return;
		if ("world".equals(System.getProperty("oldfaithful.fixes.scenario"))) WorldFixes.tick((ServerWorld) (Object) this);
		else if ("squids".equals(System.getProperty("oldfaithful.fixes.scenario"))) SquidWatch.tick((ServerWorld) (Object) this);
		else if (System.getProperty("oldfaithful.fixes.scenario", "").startsWith("bulk")) TickCensus.tick((ServerWorld) (Object) this);
		else Scenario.tick((ServerWorld) (Object) this);
	}
}
