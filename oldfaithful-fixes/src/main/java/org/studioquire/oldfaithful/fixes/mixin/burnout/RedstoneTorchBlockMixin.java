package org.studioquire.oldfaithful.fixes.mixin.burnout;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #44: a redstone torch that turns off eight times within 100 ticks burns out. The record of turn-offs is one list for
 * the whole session, pruned only from its front and only of entries more than 100 ticks old. After opening a world whose
 * clock is behind the last one's, or setting the time back, the front entries lay in the future and nothing was pruned
 * again until the clock caught up: every torch that turned off eight times since was burnt out for good. Entries from
 * the future are dropped first now; the burnout rule itself is unchanged.
 */
@Mixin(RedstoneTorchBlock.class)
abstract class RedstoneTorchBlockMixin {
	@Shadow
	private static List<Object> burnoutEntries;

	@Inject(method = "onTick", at = @At("HEAD"))
	private void oldfaithful$forgetFuture(World world, int x, int y, int z, Random random, CallbackInfo ci) {
		long now = world.getTime();
		for (Iterator<Object> it = burnoutEntries.iterator(); it.hasNext();) {
			if (((BurnoutEntryAccessor) it.next()).oldfaithful$time() > now) it.remove();
		}
	}
}
