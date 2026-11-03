package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Bench;
import org.studioquire.oldfaithful.selftest.StartupCheck;

/** Drives the start-up test and the benchmark from the game's own tick; does nothing unless one launched the game. */
@Mixin(Minecraft.class)
abstract class MinecraftStartupMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void oldfaithful$startupTest(CallbackInfo ci) {
		if (StartupCheck.ENABLED) StartupCheck.tick((Minecraft) (Object) this);
		if (Bench.ENABLED) Bench.tick((Minecraft) (Object) this);
	}
}
