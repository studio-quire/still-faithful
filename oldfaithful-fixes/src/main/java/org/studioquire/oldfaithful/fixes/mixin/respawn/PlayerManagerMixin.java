package org.studioquire.oldfaithful.fixes.mixin.respawn;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.fixes.StaleEntities;

/** #49 on a server ({@link StaleEntities}): on respawn, first clear what the client was left holding since death. */
@Mixin(PlayerManager.class)
abstract class PlayerManagerMixin {
	@Inject(method = "respawnPlayer", at = @At("HEAD"))
	private void oldfaithful$clearStale(ServerPlayerEntity player, int dimensionId, CallbackInfoReturnable<ServerPlayerEntity> cir) {
		StaleEntities.clear(player);
	}
}
