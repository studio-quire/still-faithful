package org.studioquire.oldfaithful.polish.mixin.boatsteering;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.play.PlayerMovePacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.BoatSteering;

/**
 * Boat Steering on a server: a rider's keys, from each move packet, for the boat that `onPlayerMove` ticks with it.
 * <p>
 * Only for a client that sends its look with every riding move, since the keys are read back through the look. Beta's
 * riding client sends its motion alone when its look changed since the last move and its motion with that same look
 * when it didn't (`MultiplayerClientPlayerEntity`, Old Faithful's #83), so two riding moves in a row that carry looks
 * that differ come only from a client that sends its look every time: from then on this connection steers. Beta's
 * client never sends that, so it paddles as beta's; so does any client that later sends a riding move without its
 * look. A riding move is the motion packet beta marks with -999 for both heights.
 * <p>
 * A move whose motion isn't keys alone (something pushed the rider) keeps the last clear keys for up to
 * {@link BoatSteering#HOLD} packets, then none, rather than read the push as a turn.
 */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
	@Shadow private ServerPlayerEntity player;

	@Unique private boolean oldfaithful$sendsLook, oldfaithful$lastHadLook;
	@Unique private float oldfaithful$lastYaw, oldfaithful$lastPitch;
	@Unique private int oldfaithful$heldKeys, oldfaithful$heldFor;

	@Inject(method = "onPlayerMove", at = @At("HEAD"))
	private void oldfaithful$readKeys(PlayerMovePacket packet, CallbackInfo ci) {
		boolean riding = packet.changePosition && packet.y == -999.0 && packet.eyeHeight == -999.0;
		boolean look = riding && packet.changeLook && Float.isFinite(packet.yaw) && Float.isFinite(packet.pitch);
		if (look && oldfaithful$lastHadLook && (packet.yaw != oldfaithful$lastYaw || packet.pitch != oldfaithful$lastPitch)) {
			oldfaithful$sendsLook = true;
		} else if (riding && !packet.changeLook) {
			oldfaithful$sendsLook = false;
		}
		oldfaithful$lastHadLook = look;
		oldfaithful$lastYaw = packet.yaw;
		oldfaithful$lastPitch = packet.pitch;
		int keys = BoatSteering.BETA;
		if (oldfaithful$sendsLook) {
			// A move that isn't a riding one (the client doesn't know yet that it rides) has no paddling in it.
			int read = look ? BoatSteering.decode(packet.x, packet.z, packet.yaw) : 0;
			if (read != BoatSteering.UNCLEAR) {
				keys = oldfaithful$heldKeys = read;
				oldfaithful$heldFor = 0;
			} else {
				keys = oldfaithful$heldFor++ < BoatSteering.HOLD ? oldfaithful$heldKeys : 0;
			}
		}
		((BoatSteering.Rider) player).oldfaithful$setBoatKeys(keys);
	}

	@Inject(method = "onPlayerMove", at = @At("RETURN"))
	private void oldfaithful$keysRead(PlayerMovePacket packet, CallbackInfo ci) {
		((BoatSteering.Rider) player).oldfaithful$setBoatKeys(BoatSteering.BETA);
	}
}
