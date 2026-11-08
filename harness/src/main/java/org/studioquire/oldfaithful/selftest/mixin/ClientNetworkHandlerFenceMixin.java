package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.play.PlayerMovePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.FenceWalk;

/** FenceWalk: counts the positions the server sends the player to (its own teleports, and any rubber-banding). */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerFenceMixin {
	@Inject(method = "onPlayerMove", at = @At("HEAD"))
	private void oldfaithful$countServerMoves(PlayerMovePacket packet, CallbackInfo ci) {
		FenceWalk.serverMoves++;
	}
}
