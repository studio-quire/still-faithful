package org.studioquire.oldfaithful.hardening.mixin.client;

import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.studioquire.oldfaithful.hardening.packet.ServerContent;

/**
 * Chunk data from a server: once it's in the chunk, every block id in the part it filled must exist in this game
 * ({@link ServerContent}). Read from the chunk rather than the packet, so a mod that changes the packet's layout
 * doesn't confuse the check.
 */
@Mixin(Chunk.class)
abstract class ChunkMixin {
	@Shadow public byte[] blocks;

	@Inject(method = "loadFromPacket", at = @At("RETURN"))
	private void oldfaithful$checkBlocks(byte[] bytes, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int offset,
			CallbackInfoReturnable<Integer> cir) {
		ServerContent.checkBlocks(blocks, minX, minY, minZ, maxX, maxY, maxZ);
	}
}
