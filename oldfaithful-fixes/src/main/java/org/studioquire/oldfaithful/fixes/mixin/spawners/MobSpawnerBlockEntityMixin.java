package org.studioquire.oldfaithful.fixes.mixin.spawners;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.play.UpdateSignPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.studioquire.oldfaithful.fixes.SpawnerMobs;

/**
 * #62, the server's half: a spawner's block entity gets an update packet (beta's server has one only for signs), sent
 * wherever a sign's is: with its chunk, and when its block changes. It carries the spawner's entity id (SpawnerMobs).
 */
@Mixin(MobSpawnerBlockEntity.class)
abstract class MobSpawnerBlockEntityMixin extends BlockEntity {
	@Override
	public Packet createUpdatePacket() {
		NbtCompound nbt = new NbtCompound();
		this.writeNbt(nbt);
		String[] lines = SpawnerMobs.lines(nbt.getString("EntityId"));
		return lines == null ? null : new UpdateSignPacket(this.x, this.y, this.z, lines);
	}
}
