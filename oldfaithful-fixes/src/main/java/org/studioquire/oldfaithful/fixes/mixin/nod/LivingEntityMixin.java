package org.studioquire.oldfaithful.fixes.mixin.nod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * #59: a mob turning its head towards what it looks at works out the pitch with its sign flipped (the result is
 * negated back), but stepped from the stored pitch without flipping it too. So it settled only for a target within
 * half a step of level; beyond that the head swung between two angles every tick (a zombie looking 45 degrees up at
 * you nodded between -45 and +15). The step now starts from the flipped pitch. Only how mobs look: their aim (arrows,
 * fireballs) is worked out apart from it.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	// lookAt's first lerpRotation is the pitch's; its first argument is the pitch to step from.
	@ModifyArg(method = "lookAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;lerpRotation(FFF)F", ordinal = 0), index = 0)
	private float oldfaithful$fromFlipped(float from) {
		return -((Entity) (Object) this).pitch;
	}
}
