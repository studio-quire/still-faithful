package org.studioquire.oldfaithful.fixes.mixin.tools;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.login.LoginHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.ServerMarker;
import org.studioquire.oldfaithful.fixes.ToolSpeeds;

/** #10: whether the server joined has the fix ({@link ServerMarker}). */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Inject(method = "onHello", at = @At("HEAD"))
	private void oldfaithful$readToolSpeedsMarker(LoginHelloPacket packet, CallbackInfo ci) {
		boolean now = ServerMarker.has(packet.username, ServerMarker.TOOL_SPEEDS);
		ToolSpeeds.serverAgrees = now || ServerMarker.has(packet.username, ServerMarker.TOOL_SPEEDS_WITHOUT_REDSTONE_ORE);
		ToolSpeeds.serverAgreesOnRedstoneOre = now;
	}
}
