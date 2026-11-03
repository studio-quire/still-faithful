package org.studioquire.oldfaithful.fixes.mixin.respawn;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.play.PlayerRespawnPacket;
import net.minecraft.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #49 on a multiplayer client, for servers without the fix ({@code StaleEntities} is the server's half): respawning
 * after death in the same dimension keeps the client's world, entities and all, while the server stopped telling it
 * about them a second after the death. What went away meanwhile stayed frozen on screen until a reconnect. The server
 * sends everything in range again on such a respawn (it makes the player anew), so the client drops every entity the
 * server gave it first. Only on a respawn from death; a respawn packet for a living player (a plugin's) is left as is.
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Shadow private Minecraft minecraft;
	@Shadow private ClientWorld world;

	@Inject(method = "onPlayerRespawn", at = @At("HEAD"))
	private void oldfaithful$dropStaleEntities(PlayerRespawnPacket packet, CallbackInfo ci) {
		if (minecraft.player == null || packet.dimensionRawId != minecraft.player.dimensionId || minecraft.player.health > 0) return;
		List<Entity> fromServer = new ArrayList<>();
		for (Object o : ((ClientWorldAccessor) world).oldfaithful$forcedEntities()) fromServer.add((Entity) o);
		for (Entity e : fromServer) {
			if (e != minecraft.player) world.removeEntity(e.id);
		}
	}
}
