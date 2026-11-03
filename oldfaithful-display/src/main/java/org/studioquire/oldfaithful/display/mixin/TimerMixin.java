package org.studioquire.oldfaithful.display.mixin;

import net.minecraft.client.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.studioquire.oldfaithful.display.FrameClock;

/** frame.smooth: the timer's "now" (whole milliseconds in vanilla) is the frame's instant from FrameClock. */
@Mixin(Timer.class)
abstract class TimerMixin {
	@ModifyVariable(method = "advance", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
	private double oldfaithful$frameInstant(double vanillaSeconds) {
		return FrameClock.enabled ? FrameClock.seconds() : vanillaSeconds;
	}
}
