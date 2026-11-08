package org.studioquire.oldfaithful.fixes.mixin.closedscreens;

import java.util.List;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.ClosedScreens;

/** #79: each slot a screen sends its player is noted with the time (ClosedScreens). */
@Mixin(ServerPlayerEntity.class)
abstract class ServerPlayerEntityMixin {
	@Shadow
	public boolean skipPacketSlotUpdates;

	@Inject(method = "onSlotUpdate", at = @At("HEAD"))
	private void oldfaithful$noteSent(ScreenHandler handler, int slot, ItemStack stack, CallbackInfo ci) {
		// Skipped while the server confirms what the client already did (a click, a use): nothing goes out.
		if (!skipPacketSlotUpdates) ((ClosedScreens.Sends) handler).oldfaithful$sent(slot);
	}

	/** The whole window, as sent after a click the server disagreed with (and on opening, which isn't counted). */
	@Inject(method = "onContentsUpdate(Lnet/minecraft/screen/ScreenHandler;Ljava/util/List;)V", at = @At("HEAD"))
	private void oldfaithful$noteSentAll(ScreenHandler handler, List stacks, CallbackInfo ci) {
		ClosedScreens.Sends sends = (ClosedScreens.Sends) handler;
		for (int i = 0; i < stacks.size(); i++) sends.oldfaithful$sent(i);
	}
}
