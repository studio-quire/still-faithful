package org.studioquire.oldfaithful.polish.mixin.skeletons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.studioquire.oldfaithful.polish.SkeletonAim;

/**
 * Skeleton Arrows ({@link SkeletonAim}). The arrow is made at the skeleton's eyes, as a player's is (ArrowEntity's
 * shooter constructor), and beta then lifts it 1.4 blocks, over the skeleton's head, and aims from there. Not lifting it
 * leaves it at the eyes, where beta's aim then starts too; the aim and spread are then made beta's in effect.
 */
@Mixin(SkeletonEntity.class)
abstract class SkeletonEntityMixin {
	@ModifyConstant(method = "attack", constant = @Constant(doubleValue = SkeletonAim.LIFT))
	private double oldfaithful$fromTheEyes(double lift) {
		return 0.0;
	}

	@WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/ArrowEntity;setVelocity(DDDFF)V"))
	private void oldfaithful$aimedAsBeta(ArrowEntity arrow, double x, double y, double z, float speed, float spread, Operation<Void> original) {
		double[] aim = SkeletonAim.aim(Math.sqrt(x * x + z * z), y, speed, spread);
		original.call(arrow, x, aim[0], z, speed, (float) aim[1]);
	}
}
