package org.studioquire.oldfaithful.fixes.mixin.minecarts;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * #31, items and arrows: a minecart collides with every entity's box as if it were solid, so a dropped item or an arrow
 * lying on the track stopped a cart dead. Items and arrows no longer block carts; mobs, players and other carts still
 * do, as in beta. (Cart-on-cart collisions are left as they are: the slip there is the polished edition's question.)
 */
@Mixin(MinecartEntity.class)
abstract class MinecartEntityMixin {
	@Inject(method = "getCollisionAgainstShape", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$passItems(Entity other, CallbackInfoReturnable<Box> cir) {
		if (other instanceof ItemEntity || other instanceof ArrowEntity) cir.setReturnValue(null);
	}
}
