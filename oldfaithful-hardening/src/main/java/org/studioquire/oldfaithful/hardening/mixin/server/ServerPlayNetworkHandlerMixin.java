package org.studioquire.oldfaithful.hardening.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.play.PlayerMovePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.packet.MoveChecks;
import org.studioquire.oldfaithful.hardening.packet.MoveLoading;
import org.studioquire.oldfaithful.hardening.util.Log;

/**
 * Movement packets the server can't safely act on end the connection ({@link MoveChecks}); the chunks a player's own
 * movement brings into view load at a bounded rate ({@link MoveLoading}).
 */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
	@Shadow private ServerPlayerEntity player;
	@Shadow private MinecraftServer server;
	@Shadow public boolean disconnected;

	/** Chunks this player's movement may still bring into view; negative until first used. */
	@Unique private double oldfaithful$loadBudget = -1;
	/** A chunk update was held back for the budget and is still owed. */
	@Unique private boolean oldfaithful$loadPending;
	/**
	 * The server teleported the player (a command, rubber-banding): the chunk update that brings the view to that
	 * place is the server's doing and costs nothing. Only that place: the free pass is for the destination's chunk.
	 */
	@Unique private boolean oldfaithful$teleported;
	@Unique private int oldfaithful$teleportChunkX, oldfaithful$teleportChunkZ;
	@Unique private boolean oldfaithful$heldLogged;

	@Shadow
	public abstract void disconnect(String reason);

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$checkMove(PlayerMovePacket packet, CallbackInfo ci) {
		if (disconnected) {
			// Already on the way out (by vanilla or by us): don't act on the rest of what this client queued.
			ci.cancel();
			return;
		}
		String problem = MoveChecks.problem(packet.changePosition, packet.x, packet.y, packet.eyeHeight, packet.z,
				packet.changeLook, packet.yaw, packet.pitch);
		if (problem != null) {
			ci.cancel();
			Log.info(player.name + " sent an impossible move (" + problem + "); disconnecting");
			disconnect("Illegal position");
		}
	}

	/** A rider's input: vanilla stores its angles on the player and saves them, so NaN ended up in the player file. */
	@Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$checkInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
		if (disconnected) {
			ci.cancel();
			return;
		}
		String problem = MoveChecks.inputProblem(packet.getSideways(), packet.getForward(), packet.getPitch(), packet.getYaw());
		if (problem != null) {
			ci.cancel();
			Log.info(player.name + " sent impossible input (" + problem + "); disconnecting");
			disconnect("Illegal position");
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void oldfaithful$refillLoadBudget(CallbackInfo ci) {
		int r = oldfaithful$viewDistance();
		double capacity = MoveLoading.capacity(r);
		oldfaithful$loadBudget = oldfaithful$loadBudget < 0 ? MoveLoading.start(r)
				: Math.min(capacity, oldfaithful$loadBudget + MoveLoading.refill(r));
		if (oldfaithful$loadPending && !disconnected) {
			oldfaithful$loadPending = false;
			if (oldfaithful$mayLoad(r)) server.playerManager.updatePlayerChunks(player);
			else oldfaithful$loadPending = true;
		}
	}

	@WrapOperation(method = "onPlayerMove", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/PlayerManager;updatePlayerChunks(Lnet/minecraft/entity/player/ServerPlayerEntity;)V"))
	private void oldfaithful$boundedLoading(PlayerManager manager, ServerPlayerEntity p, Operation<Void> original) {
		if (oldfaithful$mayLoad(oldfaithful$viewDistance())) {
			oldfaithful$loadPending = false;
			original.call(manager, p);
		} else {
			oldfaithful$loadPending = true;
			if (!oldfaithful$heldLogged) {
				oldfaithful$heldLogged = true;
				Log.info(player.name + " is moving faster than chunks may load for them; their chunk updates wait for the budget");
			}
		}
	}

	@Inject(method = "teleport", at = @At("HEAD"))
	private void oldfaithful$serverTeleport(double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
		oldfaithful$teleported = true;
		oldfaithful$teleportChunkX = (int) x >> 4;
		oldfaithful$teleportChunkZ = (int) z >> 4;
	}

	/**
	 * Whether the chunk map's update for the player's current position fits the budget, charging it if so. Mirrors
	 * vanilla's own test (ChunkMap.updatePlayerChunks acts only after 8 blocks and a change of centre chunk).
	 */
	@Unique
	private boolean oldfaithful$mayLoad(int r) {
		double mx = player.lastX - player.x;
		double mz = player.lastZ - player.z;
		if (mx * mx + mz * mz < 64.0) return true;
		int cx = (int) player.x >> 4, cz = (int) player.z >> 4;
		int dx = cx - ((int) player.lastX >> 4);
		int dz = cz - ((int) player.lastZ >> 4);
		if (dx == 0 && dz == 0) return true;
		boolean free = oldfaithful$teleported && cx == oldfaithful$teleportChunkX && cz == oldfaithful$teleportChunkZ;
		oldfaithful$teleported = false;
		if (free) return true;
		int cost = MoveLoading.newChunks(r, dx, dz);
		if (oldfaithful$loadBudget < 0) oldfaithful$loadBudget = MoveLoading.start(r);
		if (cost > oldfaithful$loadBudget) return false;
		oldfaithful$loadBudget -= cost;
		return true;
	}

	@Unique
	private int oldfaithful$viewDistance() {
		return (server.playerManager.getBlockViewDistance() + 16) / 16;
	}
}
