package org.studioquire.oldfaithful.polish.mixin.fences;

import net.minecraft.server.network.ServerLoginNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.studioquire.oldfaithful.polish.ServerMarker;

/** Tells joining clients this server's fences are thin to walk into ({@link ServerMarker}). */
@Mixin(ServerLoginNetworkHandler.class)
abstract class ServerLoginNetworkHandlerMixin {
	@ModifyArg(method = "accept",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/login/LoginHelloPacket;<init>(Ljava/lang/String;IJB)V"),
			index = 0)
	private String oldfaithful$markFences(String name) {
		return ServerMarker.add(name, ServerMarker.FENCES);
	}
}
