package org.studioquire.oldfaithful.fixes.mixin.respawn;

import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.entity.EntityTracker;
import net.minecraft.server.entity.EntityTrackerEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.StaleEntities;

/**
 * #49 on a server ({@link StaleEntities}): a dead player's body is removed from the world after the death animation,
 * and vanilla then quietly drops them from every entity's viewers, without telling their client. What it was seeing
 * is noted here, to be cleared from that client when they respawn.
 */
@Mixin(EntityTracker.class)
abstract class EntityTrackerMixin {
	@Shadow private Set entries;

	@Inject(method = "onEntityRemoved", at = @At("HEAD"))
	private void oldfaithful$noteWhatTheDeadSaw(Entity entity, CallbackInfo ci) {
		if (!(entity instanceof ServerPlayerEntity) || ((ServerPlayerEntity) entity).health > 0) return;
		for (Object o : entries) {
			EntityTrackerEntry entry = (EntityTrackerEntry) o;
			if (entry.listeners.contains(entity)) StaleEntities.note((ServerPlayerEntity) entity, entry.currentTrackedEntity.id);
		}
	}
}
