package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.TickRate;

/**
 * Faster game time for tests that wait on it ({@code -Ptest.speed}, docs/building.md): with
 * {@code -Doldfaithful.test.tickMs=N}, a single player world ticks every N ms instead of 50. On a server the client
 * goes by what the server says (ClientNetworkHandlerTickRateMixin).
 */
@Mixin(Minecraft.class)
abstract class MinecraftTickRateMixin {
	@Inject(method = "startGame", at = @At("HEAD"))
	private void oldfaithful$singlePlayerTicks(String worldName, String name, long seed, CallbackInfo ci) {
		TickRate.ms = TickRate.SINGLE_PLAYER_MS;
		((MinecraftTimerAccessor) this).oldfaithful$timer().tps = 1000.0F / TickRate.ms;
	}
}
