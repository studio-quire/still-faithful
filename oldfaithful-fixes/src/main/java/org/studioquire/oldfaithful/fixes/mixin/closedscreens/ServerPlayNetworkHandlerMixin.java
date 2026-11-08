package org.studioquire.oldfaithful.fixes.mixin.closedscreens;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.ClosedScreens;

/**
 * #79: when the client closes a chest, furnace, dispenser or workbench, the player's own screen takes what that one
 * last sent for the slots both show, instead of resending them from before it opened (ClosedScreens).
 */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
	@WrapOperation(method = "onCloseScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ServerPlayerEntity;onHandledScreenClosed()V"))
	private void oldfaithful$carrySent(ServerPlayerEntity player, Operation<Void> original) {
		ScreenHandler closing = player.currentScreenHandler;
		original.call(player);
		if (closing != player.playerScreenHandler && player.currentScreenHandler == player.playerScreenHandler) ClosedScreens.carry(player, closing);
	}
}
