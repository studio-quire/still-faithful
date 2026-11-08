package org.studioquire.oldfaithful.selftest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.studioquire.oldfaithful.selftest.TickRate;

/**
 * Beta's network writer sends what's queued, sleeps 100 ms, then flushes: every packet waits about that long, two ticks
 * at the game's pace. With faster ticks ({@link TickRate}) the sleep shrinks with them, so a round trip takes as many
 * ticks as it did; a test waiting a number of ticks for an answer would otherwise wait in vain.
 */
@Mixin(targets = "net.minecraft.network.Connection$1")
abstract class ConnectionWriterTickRateMixin {
	@ModifyConstant(method = "run", constant = @Constant(longValue = 100L))
	private long oldfaithful$sleepMs(long ms) {
		return Math.max(1L, ms * TickRate.ms / 50);
	}
}
