package org.studioquire.oldfaithful.fixes.mixin.dyingmobs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * #81: a mob that has died stays in the world for its fall (20 ticks), and beta counts it solid until it's removed
 * (isPushable is !dead): it pushed and was pushed. From its health reaching 0 (a client hears that from the server's
 * death status) it isn't pushable, and its own push loop in tickMovement passes over everything. Projectiles:
 * {@link ProjectileMixin}.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	public int health;

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$deadNotPushed(CallbackInfoReturnable<Boolean> cir) {
		if (health <= 0) cir.setReturnValue(false);
	}

	@WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isPushable()Z"))
	private boolean oldfaithful$deadPushesNothing(Entity other, Operation<Boolean> isPushable) {
		return health > 0 && isPushable.call(other);
	}
}
