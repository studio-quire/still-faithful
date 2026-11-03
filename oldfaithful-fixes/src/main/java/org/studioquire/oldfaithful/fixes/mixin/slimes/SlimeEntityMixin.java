package org.studioquire.oldfaithful.fixes.mixin.slimes;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #25: a big slime splits on death only if its health is exactly zero, so a hit bigger than what it had left (taking
 * health below zero) killed it whole. Any health at or below zero now counts as killed. Removal without dying
 * (despawning, peaceful, the void) leaves health above zero and still doesn't split.
 */
@Mixin(SlimeEntity.class)
abstract class SlimeEntityMixin {
	@ModifyExpressionValue(method = "markDead", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/mob/SlimeEntity;health:I"))
	private int oldfaithful$overkillIsKilled(int health) {
		return Math.max(health, 0);
	}
}
