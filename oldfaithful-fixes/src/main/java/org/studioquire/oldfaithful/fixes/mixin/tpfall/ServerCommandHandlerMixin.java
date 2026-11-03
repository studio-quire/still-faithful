package org.studioquire.oldfaithful.fixes.mixin.tpfall;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.command.ServerCommandHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #60 (server): /tp moved the player without clearing the fall they had built up, so a player teleported mid-fall
 * took that fall as damage on the next landing, wherever they arrived (20 blocks fallen: 16 damage). The command now
 * clears it, as a respawn does. Only /tp: the server's other teleports (sending back a player who moved wrongly) keep
 * beta's behaviour, so a fall can't be shed by provoking one.
 */
@Mixin(ServerCommandHandler.class)
abstract class ServerCommandHandlerMixin {
	@WrapOperation(method = "executeCommand", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;teleport(DDDFF)V"))
	private void oldfaithful$clearFall(ServerPlayNetworkHandler handler, double x, double y, double z, float yaw, float pitch,
			Operation<Void> original) {
		((EntityAccessor) ((ServerPlayNetworkHandlerAccessor) handler).oldfaithful$player()).oldfaithful$setFallDistance(0.0F);
		original.call(handler, x, y, z, yaw, pitch);
	}
}
