package org.studioquire.oldfaithful.fixes.mixin.explosions;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #58 (server): an explosion hurts each entity, then adds its push to the entity's velocity. A server sends a player
 * their velocity only when it's marked changed, which the damage does only for a fresh hit; within the half second
 * after earlier damage (or the seconds of protection after joining or respawning) it isn't, and since a client never
 * runs the explosion itself, the push was lost: chained TNT, a creeper right after a zombie's hit. Each push on a player
 * is now marked for sending, as a fresh hit's is. Vanilla clients take it as any knockback.
 */
@Mixin(Explosion.class)
abstract class ExplosionMixin {
	@Inject(method = "explode", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;velocityZ:D",
			opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void oldfaithful$sendPush(CallbackInfo ci, @Local(ordinal = 0) Entity entity) {
		if (entity instanceof PlayerEntity) entity.velocityModified = true;
	}
}
