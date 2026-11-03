package org.studioquire.oldfaithful.fixes.mixin.spawners;

import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.play.UpdateSignPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.SpawnerMobs;

/**
 * #62, the client's half: a client made each spawner's block entity itself, whose constructor says "Pig", so every
 * dungeon showed a spinning pig. A server with the fix sends the spawner's entity id (SpawnerMobs); it's applied only
 * where this client has a spawner, so a real sign whose text happens to match is left to beta's handling.
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientNetworkHandlerMixin {
	@Shadow private Minecraft minecraft;

	@Inject(method = "handleUpdateSign", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$spawnerMob(UpdateSignPacket packet, CallbackInfo ci) {
		String id = SpawnerMobs.entityId(packet.text);
		if (id == null || this.minecraft.world == null || !this.minecraft.world.isPosLoaded(packet.x, packet.y, packet.z)) return;
		if (this.minecraft.world.getBlockId(packet.x, packet.y, packet.z) != Block.SPAWNER.id) return;
		BlockEntity be = this.minecraft.world.getBlockEntity(packet.x, packet.y, packet.z);
		if (be instanceof MobSpawnerBlockEntity) ((MobSpawnerBlockEntity) be).setSpawnedEntityId(id);
		ci.cancel();
	}
}
