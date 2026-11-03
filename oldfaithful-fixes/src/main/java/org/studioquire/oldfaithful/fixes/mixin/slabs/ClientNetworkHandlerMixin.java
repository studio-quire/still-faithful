package org.studioquire.oldfaithful.fixes.mixin.slabs;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.login.LoginHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.ServerMarker;
import org.studioquire.oldfaithful.fixes.WoodenSlabs;

/** #9: whether the server joined has the fix ({@link ServerMarker}). */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Inject(method = "onHello", at = @At("HEAD"))
	private void oldfaithful$readWoodenSlabsMarker(LoginHelloPacket packet, CallbackInfo ci) {
		WoodenSlabs.serverAgrees = ServerMarker.has(packet.username, ServerMarker.WOODEN_SLABS);
	}
}
