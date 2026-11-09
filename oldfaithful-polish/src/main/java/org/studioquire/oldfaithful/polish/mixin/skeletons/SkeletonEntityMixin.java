package org.studioquire.oldfaithful.polish.mixin.skeletons;

import net.minecraft.entity.mob.SkeletonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The arrow is made at the skeleton's eyes, as a player's is (ArrowEntity's shooter constructor), and beta then lifts
 * it 1.4 blocks, over the skeleton's head; the aim is worked out from where it then is. Not lifting it leaves it at the
 * eyes, aimed from there, with beta's speed and spread.
 */
@Mixin(SkeletonEntity.class)
abstract class SkeletonEntityMixin {
	@ModifyConstant(method = "attack", constant = @Constant(doubleValue = 1.399999976158142))
	private double oldfaithful$fromTheEyes(double lift) {
		return 0.0;
	}
}
