package org.studioquire.oldfaithful.fixes.mixin.miningdelay;

import net.minecraft.client.MultiplayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #36, on a multiplayer client: after a block breaks, mining pauses 5 ticks before the next. In single player that
 * pause counts down while the button is held and is cleared on letting go. In multiplayer it only counted down while
 * a block was being mined, and nothing cleared it, so every block after the first in a session started with 5 dead
 * ticks however long ago the last break was. Now it counts down and clears as in single player.
 * <p>
 * Not changed: multiplayer still starts the next block on the held click's repeat (every 5 ticks), not at once as
 * single player does; that tempo is what servers know (Still Faithful's question). A server checks only each block's
 * own mining time, which this doesn't shorten, so a vanilla server accepts it.
 */
@Mixin(MultiplayerInteractionManager.class)
abstract class MultiplayerInteractionManagerMixin {
	@Shadow private int breakingDelayTicks;
	@Shadow private boolean breakingBlock;

	@Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
	private void oldfaithful$pauseCountsDown(int x, int y, int z, int side, CallbackInfo ci) {
		// While mining, vanilla counts it down itself.
		if (!breakingBlock && breakingDelayTicks > 0) breakingDelayTicks--;
	}

	@Inject(method = "cancelBlockBreaking", at = @At("HEAD"))
	private void oldfaithful$letGoClears(CallbackInfo ci) {
		breakingDelayTicks = 0;
	}
}
