package org.studioquire.oldfaithful.fixes.mixin.signs;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #48: when 2 to 9 blocks of a chunk change in one tick, the server sends them as one packet and then looks for block
 * entities to send with them, but reads each position from the change count instead of from the changed block, so it
 * looks in the wrong place. Signs are the only block entity with an update packet, and an edited sign reaches other
 * players only through this path: a sign edited in the same tick as any other change in its chunk (flowing water,
 * crops, redstone) stayed blank to everyone else until the chunk was sent again. Vanilla's loop is skipped (along with
 * its leftover "Sending!" print) and the changed positions are read instead.
 */
@Mixin(targets = "net.minecraft.server.ChunkMap$TrackedChunk")
abstract class TrackedChunkMixin {
	@Shadow private short[] dirtyBlocks;
	@Shadow private int dirtyBlockCount;
	@Shadow private int chunkX;
	@Shadow private int chunkZ;

	@Shadow
	private void sendBlockEntityUpdate(BlockEntity blockEntity) {
	}

	/** Vanilla's loop over the changes, after the multi-block packet: it runs no times. */
	@ModifyExpressionValue(method = "updateChunk", at = @At(value = "FIELD",
			target = "Lnet/minecraft/server/ChunkMap$TrackedChunk;dirtyBlockCount:I", ordinal = 0),
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/network/packet/s2c/play/ChunkDeltaUpdateS2CPacket;<init>(II[SILnet/minecraft/world/World;)V")))
	private int oldfaithful$skipVanillaLoop(int count) {
		return 0;
	}

	/** Right after the multi-block packet is sent, so each client has the block before its block entity. */
	@Inject(method = "updateChunk", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/ChunkMap$TrackedChunk;sendPacketToPlayers(Lnet/minecraft/network/packet/Packet;)V",
			ordinal = 0, shift = At.Shift.AFTER),
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/network/packet/s2c/play/ChunkDeltaUpdateS2CPacket;<init>(II[SILnet/minecraft/world/World;)V")))
	private void oldfaithful$sendChangedBlockEntities(CallbackInfo ci, @Local ServerWorld world) {
		for (int i = 0; i < dirtyBlockCount; i++) {
			short packed = dirtyBlocks[i];
			int x = chunkX * 16 + (packed >> 12 & 15);
			int y = packed & 0xFF;
			int z = chunkZ * 16 + (packed >> 8 & 15);
			if (Block.BLOCKS_WITH_ENTITY[world.getBlockId(x, y, z)]) sendBlockEntityUpdate(world.getBlockEntity(x, y, z));
		}
	}
}
