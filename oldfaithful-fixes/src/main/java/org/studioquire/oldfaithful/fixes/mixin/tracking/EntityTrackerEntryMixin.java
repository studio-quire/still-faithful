package org.studioquire.oldfaithful.fixes.mixin.tracking;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.entity.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #16a: who gets to see an entity was decided from {@code lastX/lastZ}, the position its viewers were last sent, not
 * where it is. On the tick a player teleports (a /tp, a portal's placement after the dimension change), their entry
 * picks viewers before that position is updated: the players at the destination are never added, and the choice
 * isn't made again until the teleporter walks 4 blocks, so to players standing there they stay invisible. Viewers are
 * now chosen by where the entity is.
 * <p>
 * A viewer added between the entry's position packets gets a spawn packet at the entity's current position, while the
 * relative moves that follow are counted from {@code lastX}; vanilla left that viewer off by the difference until the
 * next absolute packet, up to 20 s later. So adding a viewer makes the entry's next position packet an absolute one
 * (vanilla's own periodic resync, brought forward), which puts every viewer on the same position.
 */
@Mixin(EntityTrackerEntry.class)
abstract class EntityTrackerEntryMixin {
	@Shadow public Entity currentTrackedEntity;
	@Shadow private int ticksSinceLastDismount;

	@ModifyVariable(method = "updateListener", at = @At("STORE"), index = 2)
	private double oldfaithful$xFromEntity(double dx, ServerPlayerEntity player) {
		return player.x - currentTrackedEntity.x;
	}

	@ModifyVariable(method = "updateListener", at = @At("STORE"), index = 4)
	private double oldfaithful$zFromEntity(double dz, ServerPlayerEntity player) {
		return player.z - currentTrackedEntity.z;
	}

	@Inject(method = "updateListener", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/entity/EntityTrackerEntry;createAddEntityPacket()Lnet/minecraft/network/packet/Packet;"))
	private void oldfaithful$resyncAfterAdding(ServerPlayerEntity player, CallbackInfo ci) {
		// Past vanilla's 400: the next position packet is absolute, and resets this.
		ticksSinceLastDismount = 401;
	}
}
