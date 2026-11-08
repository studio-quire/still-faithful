package org.studioquire.oldfaithful.polish.mixin.fences;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.login.LoginHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Fences;
import org.studioquire.oldfaithful.polish.ServerMarker;

/** Whether the server joined has thin fences to walk into ({@link ServerMarker}). */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Inject(method = "onHello", at = @At("HEAD"))
	private void oldfaithful$readFencesMarker(LoginHelloPacket packet, CallbackInfo ci) {
		Fences.serverAgrees = ServerMarker.has(packet.username, ServerMarker.FENCES);
	}
}
