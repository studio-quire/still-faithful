package org.studioquire.oldfaithful.polish.mixin.glowstone;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.login.LoginHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Glowstone;
import org.studioquire.oldfaithful.polish.ServerMarker;

/** Whether the server joined has the glowstone change ({@link ServerMarker}). */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Inject(method = "onHello", at = @At("HEAD"))
	private void oldfaithful$readGlowstoneMarker(LoginHelloPacket packet, CallbackInfo ci) {
		Glowstone.serverAgrees = ServerMarker.has(packet.username, ServerMarker.GLOWSTONE);
	}
}
