package org.studioquire.oldfaithful.hardening.mixin;

import java.io.DataInputStream;
import java.io.IOException;

import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.studioquire.oldfaithful.hardening.packet.PacketLimits;

/**
 * Guards against a server crashing the client with one chunk packet: the game allocates whatever compressed length
 * the server states (up to 2 GB) before reading it. Checked right after the length is read, before the allocation.
 */
@Mixin(ChunkDataS2CPacket.class)
abstract class ChunkDataS2CPacketMixin {
	@Shadow public int sizeX;
	@Shadow public int sizeY;
	@Shadow public int sizeZ;
	@Shadow private int chunkDataSize;

	@Inject(method = "read", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
			target = "Lnet/minecraft/network/packet/s2c/play/ChunkDataS2CPacket;chunkDataSize:I", shift = At.Shift.AFTER))
	private void oldfaithful$checkLength(DataInputStream stream, CallbackInfo ci) throws IOException {
		PacketLimits.checkChunkData(chunkDataSize, sizeX, sizeY, sizeZ);
	}
}
