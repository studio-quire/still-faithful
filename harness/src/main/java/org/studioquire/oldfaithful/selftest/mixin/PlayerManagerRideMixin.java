package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.MultiplayerScenes;

/**
 * :harness:multiplayerTest only: a player whose name starts with "Rider" joins sitting in a boat, so the test's raw
 * client can send what a riding client sends; one whose name starts with "Sky" joins in the air above any terrain.
 */
@Mixin(PlayerManager.class)
abstract class PlayerManagerRideMixin {
	/** Above any block a b1.7.3 world can hold (height 128), so moves through the air can't touch terrain. */
	private static final double SKY = 130.0;

	/**
	 * A player whose name starts with "Sky" joins in the open air above their spawn: placed before the server tracks
	 * their chunks and sends them their position, so the client starts there. Movement scenarios then can't be
	 * rubber-banded by whatever terrain the (random) world put at spawn.
	 */
	@Inject(method = "addPlayer", at = @At("HEAD"))
	private void oldfaithful$placeInSky(ServerPlayerEntity player, CallbackInfo ci) {
		boolean client = player.name.equals(MultiplayerScenes.CLIENT);
		if (!Boolean.getBoolean("oldfaithful.selftest.multiplayer") || !(player.name.startsWith("Sky") || client)) return;
		double y = client ? MultiplayerScenes.FLOOR + 1 : SKY;
		player.setPositionAndAngles(player.x, y, player.z, player.yaw, player.pitch);
		System.out.println("[oldfaithful-selftest] " + player.name + " placed in the sky at y " + y);
		// :harness:fixesMultiplayerTest's hidden client stands on a scene of its own.
		if (client) MultiplayerScenes.buildClientScene(player);
	}

	@Inject(method = "addPlayer", at = @At("RETURN"))
	private void oldfaithful$seatRider(ServerPlayerEntity player, CallbackInfo ci) {
		if (!Boolean.getBoolean("oldfaithful.selftest.multiplayer") || !player.name.startsWith("Rider")) return;
		BoatEntity boat = new BoatEntity(player.world, player.x, player.y, player.z);
		player.world.spawnEntity(boat);
		player.setVehicle(boat);
		System.out.println("[oldfaithful-selftest] " + player.name + " seated in a boat (entity " + boat.id + ")");
	}
}
