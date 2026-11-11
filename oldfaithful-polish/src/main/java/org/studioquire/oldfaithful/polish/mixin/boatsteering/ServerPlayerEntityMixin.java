package org.studioquire.oldfaithful.polish.mixin.boatsteering;

import net.minecraft.entity.player.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.studioquire.oldfaithful.polish.BoatSteering;

/** Boat Steering on a server: the keys read from the move packet being handled ({@link ServerPlayNetworkHandlerMixin}). */
@Mixin(ServerPlayerEntity.class)
abstract class ServerPlayerEntityMixin implements BoatSteering.Rider {
	@Unique private int oldfaithful$boatKeys = BoatSteering.BETA;

	@Override
	public int oldfaithful$boatKeys() {
		return oldfaithful$boatKeys;
	}

	@Override
	public void oldfaithful$setBoatKeys(int keys) {
		oldfaithful$boatKeys = keys;
	}
}
