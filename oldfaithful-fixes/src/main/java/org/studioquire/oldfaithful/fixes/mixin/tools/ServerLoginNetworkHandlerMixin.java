package org.studioquire.oldfaithful.fixes.mixin.tools;

import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.studioquire.oldfaithful.fixes.ServerMarker;

/** #10: tells joining clients this server has the fix ({@link ServerMarker}). */
@Mixin(ServerLoginNetworkHandler.class)
abstract class ServerLoginNetworkHandlerMixin {
	@ModifyArg(method = "accept",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/login/LoginHelloPacket;<init>(Ljava/lang/String;IJB)V"),
			index = 0)
	private String oldfaithful$markToolSpeeds(String name) {
		return ServerMarker.add(name, ServerMarker.TOOL_SPEEDS);
	}
}
