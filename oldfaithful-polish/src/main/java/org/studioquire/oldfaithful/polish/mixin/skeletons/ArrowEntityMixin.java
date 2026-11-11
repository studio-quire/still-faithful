package org.studioquire.oldfaithful.polish.mixin.skeletons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Skeleton Arrows: a spider jockey's arrow passes its own spider. Beta's arrow spares only its shooter, for its first
 * four ticks; from the eyes, a jockey's arrow starts 0.4 over its spider's box (as the arrow tests it) where beta's
 * started 1.8 over, so shooting steeply down at a player, it nearly always hit the spider (`tools/skeleton_arrows.py
 * jockey`). Here a skeleton's mount is spared as the skeleton is, as later versions spare whatever the shooter rides.
 */
@Mixin(ArrowEntity.class)
abstract class ArrowEntityMixin {
	@Shadow
	public LivingEntity owner;
	@Shadow
	private int inAirTime;

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isCollidable()Z"))
	private boolean oldfaithful$pastItsMount(Entity hit, Operation<Boolean> isCollidable) {
		if (owner instanceof SkeletonEntity && hit == owner.vehicle && inAirTime < 5) return false;
		return isCollidable.call(hit);
	}
}
