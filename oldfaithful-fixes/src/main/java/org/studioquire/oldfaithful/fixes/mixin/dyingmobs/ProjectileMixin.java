package org.studioquire.oldfaithful.fixes.mixin.dyingmobs;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #81: projectiles fly through a mob that has died. Beta's take any entity that's collidable, and a dying mob is until
 * it's removed, while its damage refuses every hit at 0 health: an arrow bounced off the falling body and was lost. The
 * crosshair still meets a dying mob, as later versions' does.
 */
@Mixin({ArrowEntity.class, SnowballEntity.class, EggEntity.class, FishingBobberEntity.class, FireballEntity.class})
abstract class ProjectileMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isCollidable()Z"))
	private boolean oldfaithful$throughTheDead(Entity hit, Operation<Boolean> isCollidable) {
		return isCollidable.call(hit) && !(hit instanceof LivingEntity && ((LivingEntity) hit).health <= 0);
	}
}
