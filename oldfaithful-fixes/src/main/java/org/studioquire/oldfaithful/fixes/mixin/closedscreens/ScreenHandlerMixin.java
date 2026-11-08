package org.studioquire.oldfaithful.fixes.mixin.closedscreens;

import java.util.Arrays;
import java.util.List;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.studioquire.oldfaithful.fixes.ClosedScreens;

/**
 * #79: when each slot was last sent to the player (ClosedScreens). What a screen sends as it opens isn't counted: it
 * follows the open packet, so the client has it before it can close the screen.
 */
@Mixin(ScreenHandler.class)
abstract class ScreenHandlerMixin implements ClosedScreens.Sends {
	@Shadow
	public List slots;

	@Unique
	private long[] oldfaithful$sentAt;
	@Unique
	private boolean oldfaithful$opening;

	@WrapMethod(method = "addListener")
	private void oldfaithful$whileOpening(ScreenHandlerListener listener, Operation<Void> original) {
		oldfaithful$opening = true;
		try {
			original.call(listener);
		} finally {
			oldfaithful$opening = false;
		}
	}

	@Override
	public void oldfaithful$sent(int slot) {
		if (oldfaithful$opening || slot < 0) return;
		if (oldfaithful$sentAt == null || oldfaithful$sentAt.length <= slot) {
			int old = oldfaithful$sentAt == null ? 0 : oldfaithful$sentAt.length;
			oldfaithful$sentAt = oldfaithful$sentAt == null ? new long[Math.max(slots.size(), slot + 1)]
					: Arrays.copyOf(oldfaithful$sentAt, Math.max(slots.size(), slot + 1));
			Arrays.fill(oldfaithful$sentAt, old, oldfaithful$sentAt.length, Long.MIN_VALUE);
		}
		oldfaithful$sentAt[slot] = System.nanoTime();
	}

	@Override
	public boolean oldfaithful$sentWithin(int slot, long nanos) {
		return oldfaithful$sentAt != null && slot >= 0 && slot < oldfaithful$sentAt.length && oldfaithful$sentAt[slot] != Long.MIN_VALUE
				&& System.nanoTime() - oldfaithful$sentAt[slot] < nanos;
	}
}
