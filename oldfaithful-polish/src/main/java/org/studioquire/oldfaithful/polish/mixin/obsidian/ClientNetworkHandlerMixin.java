package org.studioquire.oldfaithful.polish.mixin.obsidian;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.login.LoginHelloPacket;
import net.minecraft.network.packet.play.UpdateSignPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Obsidian;
import org.studioquire.oldfaithful.polish.ServerMarker;

/** Which obsidian the server joined has: beta's from login, until a sign packet says otherwise ({@link ServerMarker#sign}). */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Inject(method = "onHello", at = @At("HEAD"))
	private void oldfaithful$forgetObsidianMarker(LoginHelloPacket packet, CallbackInfo ci) {
		Obsidian.serverMode = Obsidian.BETA;
	}

	@Inject(method = "handleUpdateSign", at = @At("HEAD"))
	private void oldfaithful$readObsidianMarker(UpdateSignPacket packet, CallbackInfo ci) {
		if (ServerMarker.signHas(packet, ServerMarker.OBSIDIAN)) Obsidian.serverMode = Obsidian.FULL;
		else if (ServerMarker.signHas(packet, ServerMarker.OBSIDIAN_DIAMOND)) Obsidian.serverMode = Obsidian.DIAMOND;
	}
}
