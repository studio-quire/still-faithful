package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.stat.StatsSyncer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every screen change saves the unsent stats, first waiting out a stats save running in the background in steps of
 * 100 ms (up to 3 s). Each step here ends as soon as that save has finished: the same wait, without up to 100 ms more
 * of the game standing still after it.
 */
@Mixin(StatsSyncer.class)
abstract class StatsSyncerMixin {
	@Shadow
	private volatile boolean busy;

	@WrapOperation(method = "saveUnsent", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"))
	private void oldfaithful$sleepWhileBusy(long ms, Operation<Void> sleep) {
		long end = System.nanoTime() + ms * 1_000_000L;
		while (busy && end - System.nanoTime() > 0) sleep.call(1L);
	}
}
