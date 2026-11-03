package org.studioquire.oldfaithful.fixes.mixin.saddle;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #29: a pig's drops are pork only, so the saddle on a saddled pig was lost when it was killed. It now drops too, with
 * the other drops (on the side that decides drops: the server, or the single-player world).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "onKilledBy", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;dropItems()V", shift = At.Shift.AFTER))
	private void oldfaithful$saddle(Entity adversary, CallbackInfo ci) {
		Object self = this;
		if (self instanceof PigEntity && ((PigEntity) self).isSaddled()) ((PigEntity) self).dropItem(Item.SADDLE.id, 1);
	}
}
