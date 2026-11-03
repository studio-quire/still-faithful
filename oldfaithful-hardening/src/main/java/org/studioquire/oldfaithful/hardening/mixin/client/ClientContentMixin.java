package org.studioquire.oldfaithful.hardening.mixin.client;

import net.minecraft.client.network.ClientNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ItemEntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.play.PlayerMovePacket;
import net.minecraft.network.packet.s2c.play.EntityVehicleSetS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.packet.ServerContent;

/**
 * Item and block ids in server packets must exist in this game, and riding mustn't loop ({@link ServerContent});
 * checked before the packet is used.
 */
@Mixin(ClientNetworkHandler.class)
abstract class ClientContentMixin {
	@Shadow private Minecraft minecraft;

	@Shadow
	private Entity getEntity(int id) {
		throw new AssertionError();
	}

	@Inject(method = "onItemEntitySpawn", at = @At("HEAD"))
	private void oldfaithful$itemEntity(ItemEntitySpawnS2CPacket packet, CallbackInfo ci) {
		ServerContent.checkItem(packet.itemRawId, packet.itemDamage);
	}

	@Inject(method = "onPlayerSpawn", at = @At("HEAD"))
	private void oldfaithful$playerSpawn(PlayerSpawnS2CPacket packet, CallbackInfo ci) {
		if (packet.itemRawId != 0) ServerContent.checkItem(packet.itemRawId, 0); // 0: empty hand
	}

	@Inject(method = "onEntityEquipmentUpdate", at = @At("HEAD"))
	private void oldfaithful$equipment(EntityEquipmentUpdateS2CPacket packet, CallbackInfo ci) {
		ServerContent.checkItem(packet.itemRawId, packet.itemDamage);
	}

	@Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
	private void oldfaithful$slot(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
		ServerContent.checkStack(packet.stack);
	}

	@Inject(method = "onInventory", at = @At("HEAD"))
	private void oldfaithful$inventory(InventoryS2CPacket packet, CallbackInfo ci) {
		ServerContent.checkStacks(packet.contents);
	}

	@Inject(method = "onEntityVehicleSet", at = @At("HEAD"))
	private void oldfaithful$riding(EntityVehicleSetS2CPacket packet, CallbackInfo ci) {
		// The same lookup the handler does next.
		Entity entity = packet.id == minecraft.player.id ? minecraft.player : getEntity(packet.id);
		if (entity != null) ServerContent.checkRiding(entity, getEntity(packet.vehicleId));
	}

	/** The player's own position and look, which the client takes as sent. */
	@Inject(method = "onPlayerMove", at = @At("HEAD"))
	private void oldfaithful$position(PlayerMovePacket packet, CallbackInfo ci) {
		if (packet.changePosition) {
			ServerContent.checkPosition("position", packet.x, packet.y, packet.z);
			ServerContent.checkPosition("position", packet.x, packet.eyeHeight, packet.z);
		}
		if (packet.changeLook) ServerContent.checkAngles("position", packet.yaw, packet.pitch);
	}

	/** The only other position a server sends as a raw number rather than a scaled integer. */
	@Inject(method = "onExplosion", at = @At("HEAD"))
	private void oldfaithful$explosion(ExplosionS2CPacket packet, CallbackInfo ci) {
		ServerContent.checkPosition("explosion", packet.x, packet.y, packet.z);
	}

	@Inject(method = "onBlockUpdate", at = @At("HEAD"))
	private void oldfaithful$block(BlockUpdateS2CPacket packet, CallbackInfo ci) {
		ServerContent.checkBlock(packet.blockRawId);
	}

	@Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
	private void oldfaithful$blocks(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
		if (packet.blockRawIds == null) return;
		int n = Math.min(packet.size, packet.blockRawIds.length);
		for (int i = 0; i < n; i++) ServerContent.checkBlock(packet.blockRawIds[i] & 0xff);
	}
}
