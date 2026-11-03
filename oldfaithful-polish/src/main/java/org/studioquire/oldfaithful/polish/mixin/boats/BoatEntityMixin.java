package org.studioquire.oldfaithful.polish.mixin.boats;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.Boats;

/**
 * #12, sturdier boats ({@link Boats}): a break needs a real crash as well as beta's own condition; and floating items
 * and arrows aren't solid to a boat (beta's boat treated every entity's box as a wall, so an item in its path stopped
 * it and could break it), as Old Faithful's #31 makes them for minecarts. Hits break a boat as in beta.
 */
@Mixin(BoatEntity.class)
abstract class BoatEntityMixin extends Entity {
	BoatEntityMixin(World world) {
		super(world);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/BoatEntity;move(DDD)V"))
	private void oldfaithful$measureImpact(BoatEntity boat, double dx, double dy, double dz, Operation<Void> original,
			@Share("impact") LocalDoubleRef impact) {
		original.call(boat, dx, dy, dz);
		impact.set(Boats.impact(dx, dz, this.velocityX, this.velocityZ));
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/vehicle/BoatEntity;horizontalCollision:Z"))
	private boolean oldfaithful$onlyRealCrashes(boolean collided, @Share("impact") LocalDoubleRef impact) {
		return collided && impact.get() >= Boats.CRASH;
	}

	@Inject(method = "getCollisionAgainstShape", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$itemsAreNotWalls(Entity other, CallbackInfoReturnable<Box> cir) {
		if (other instanceof ItemEntity || other instanceof ArrowEntity) cir.setReturnValue(null);
	}
}
