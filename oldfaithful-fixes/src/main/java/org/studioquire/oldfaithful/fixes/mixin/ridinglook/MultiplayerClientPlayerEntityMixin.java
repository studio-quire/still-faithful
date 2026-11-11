package org.studioquire.oldfaithful.fixes.mixin.ridinglook;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.client.network.MultiplayerClientPlayerEntity;
import net.minecraft.client.util.Session;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.play.PlayerMoveFullPacket;
import net.minecraft.network.packet.play.PlayerMovePositionAndOnGroundPacket;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #83, on a multiplayer client: while riding, each tick's move packet carries the rider's motion (marked with -999 for
 * both heights) and was meant to carry the look when it changed; beta's test is the wrong way round, sending the motion
 * alone whenever the look changed since the last tick and the motion with the look only when it hadn't. So a rider
 * turning their view (or turned by the vehicle) never told the server where they looked until they held still: others
 * never saw their head turn, and the server's own idea of their yaw drifted (it turns a rider with the vehicle twice a
 * packet). The motion-only packet becomes beta's own full one, the motion with the look, every riding tick, which any
 * server reads as beta's sends it when the look holds still.
 */
@Mixin(MultiplayerClientPlayerEntity.class)
abstract class MultiplayerClientPlayerEntityMixin extends ClientPlayerEntity {
	MultiplayerClientPlayerEntityMixin(Minecraft minecraft, World world, Session session, int dimension) {
		super(minecraft, world, session, dimension);
	}

	@WrapOperation(method = "method_1922", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
	private void oldfaithful$withLook(ClientNetworkHandler handler, Packet packet, Operation<Void> original) {
		if (this.vehicle != null && packet instanceof PlayerMovePositionAndOnGroundPacket) {
			PlayerMovePositionAndOnGroundPacket motion = (PlayerMovePositionAndOnGroundPacket) packet;
			if (motion.y == -999.0 && motion.eyeHeight == -999.0) {
				packet = new PlayerMoveFullPacket(motion.x, -999.0, -999.0, motion.z, this.yaw, this.pitch, motion.onGround);
			}
		}
		original.call(handler, packet);
	}
}
