package org.studioquire.oldfaithful.fixes.mixin.refusedbreaks;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #5: a start or finish of mining more than 6 blocks from where the server has the player (lag, a rubber-band) is
 * refused without a word, while the client has already taken the block away; it stays a hole on that client for 4 s,
 * until the client undoes its own unconfirmed change. Every other refusal (spawn protection, a finish too early, a
 * placement) sends the block back at once; this one now does too. Only within 16 blocks of the player, vanilla's own
 * reach for answering an aborted break, so a client can't make the server read blocks anywhere.
 */
@Mixin(ServerPlayNetworkHandler.class)
abstract class ServerPlayNetworkHandlerMixin {
	@Shadow private ServerPlayerEntity player;
	@Shadow private MinecraftServer server;

	@Inject(method = "handlePlayerAction", at = @At("HEAD"))
	private void oldfaithful$answerRefusedBreak(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (packet.action != 0 && packet.action != 2) return;
		double dx = player.x - (packet.x + 0.5);
		double dy = player.y - (packet.y + 0.5);
		double dz = player.z - (packet.z + 0.5);
		double d2 = dx * dx + dy * dy + dz * dz;
		// Vanilla refuses (returns) right after this for more than 36; we answer first.
		if (d2 > 36.0 && d2 < 256.0 && packet.y >= 0 && packet.y < 128) {
			player.networkHandler.sendPacket(new BlockUpdateS2CPacket(packet.x, packet.y, packet.z, server.getWorld(player.dimensionId)));
		}
	}
}
