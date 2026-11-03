package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.packet.play.ChatMessagePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.MultiplayerScenes;

/** :harness:fixesMultiplayerTest only: "!oftest" chat commands ({@link MultiplayerScenes}). */
@Mixin(ServerPlayNetworkHandler.class)
abstract class MultiplayerScenesMixin {
	@Shadow private ServerPlayerEntity player;
	@Shadow private MinecraftServer server;

	@Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$testCommand(ChatMessagePacket packet, CallbackInfo ci) {
		if (MultiplayerScenes.ENABLED && MultiplayerScenes.command(server, player, packet.chatMessage)) ci.cancel();
	}
}
