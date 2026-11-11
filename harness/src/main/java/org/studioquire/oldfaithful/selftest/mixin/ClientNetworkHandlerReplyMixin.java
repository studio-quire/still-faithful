package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.play.ChatMessagePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.BoatRide;

/** BoatRide: a test server's answers ("oftest ..." chat lines, MultiplayerScenes) taken for the test, not shown. */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerReplyMixin {
	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$testReply(ChatMessagePacket packet, CallbackInfo ci) {
		if (packet.chatMessage != null && packet.chatMessage.startsWith("oftest ")) {
			BoatRide.reply = packet.chatMessage;
			ci.cancel();
		}
	}
}
