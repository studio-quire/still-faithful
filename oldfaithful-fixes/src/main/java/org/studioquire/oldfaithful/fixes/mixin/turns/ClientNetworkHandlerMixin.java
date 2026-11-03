package org.studioquire.oldfaithful.fixes.mixin.turns;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.TurnTarget;

/**
 * #50, on a multiplayer client: a position packet that carries no rotation used the entity's current rotation as the
 * new target, which is where it stood mid-turn. The server sends players every 2 ticks and the client turns them over
 * 3, so a move right after a turn cut off the last third of it, and with the server sending a turn only once it
 * reaches 11.25 degrees, other players were left facing up to that far off (a sudden turn: a third of it), until they
 * turned again. Living entities (players and mobs) now keep the last rotation the server sent as the target. The
 * 11.25-degree step itself is beta's (Still Faithful's question).
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@WrapOperation(method = "onEntity", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;setPositionAndAnglesAvoidEntities(DDDFFI)V"))
	private void oldfaithful$keepTurnTarget(Entity entity, double x, double y, double z, float yaw, float pitch, int steps,
			Operation<Void> original, @Local(argsOnly = true) EntityS2CPacket packet) {
		if (entity instanceof LivingEntity) {
			TurnTarget target = (TurnTarget) entity;
			if (packet.rotate) {
				target.oldfaithful$setTurnTarget(yaw, pitch);
			} else if (target.oldfaithful$hasTurnTarget()) {
				yaw = target.oldfaithful$turnYaw();
				pitch = target.oldfaithful$turnPitch();
			}
		}
		original.call(entity, x, y, z, yaw, pitch, steps);
	}

	@WrapOperation(method = "onEntityPosition", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/Entity;setPositionAndAnglesAvoidEntities(DDDFFI)V"))
	private void oldfaithful$noteTurnTarget(Entity entity, double x, double y, double z, float yaw, float pitch, int steps,
			Operation<Void> original) {
		if (entity instanceof LivingEntity) ((TurnTarget) entity).oldfaithful$setTurnTarget(yaw, pitch);
		original.call(entity, x, y, z, yaw, pitch, steps);
	}
}
