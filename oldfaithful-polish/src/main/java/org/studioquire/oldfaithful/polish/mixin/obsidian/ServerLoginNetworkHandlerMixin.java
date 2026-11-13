package org.studioquire.oldfaithful.polish.mixin.obsidian;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.login.LoginHelloPacket;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.Obsidian;
import org.studioquire.oldfaithful.polish.ServerMarker;

/** Tells a joining client which obsidian this server has, straight after the login packet ({@link ServerMarker#sign}). */
@Mixin(ServerLoginNetworkHandler.class)
abstract class ServerLoginNetworkHandlerMixin {
	@WrapOperation(method = "accept",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
	private void oldfaithful$markObsidian(ServerPlayNetworkHandler handler, Packet packet, Operation<Void> original) {
		original.call(handler, packet);
		if (packet instanceof LoginHelloPacket) {
			handler.sendPacket(ServerMarker.sign(Obsidian.ownMode() == Obsidian.DIAMOND ? ServerMarker.OBSIDIAN_DIAMOND : ServerMarker.OBSIDIAN));
		}
	}
}
