package org.studioquire.oldfaithful.hardening.mixin;

import java.io.IOException;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import org.studioquire.oldfaithful.hardening.packet.PacketLimits;

/**
 * Guards against a server exhausting the client's memory with one explosion packet: the game reads as many block
 * entries as the server's count says, each costing far more memory than its three bytes on the wire.
 */
@Mixin(ExplosionS2CPacket.class)
abstract class ExplosionS2CPacketMixin {
	@ModifyExpressionValue(method = "read", at = @At(value = "INVOKE", target = "Ljava/io/DataInputStream;readInt()I"))
	private int oldfaithful$checkCount(int count) throws IOException {
		PacketLimits.checkExplosion(count);
		return count;
	}
}
