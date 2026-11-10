package org.studioquire.oldfaithful.polish.mixin.entitydistance;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Beta draws a thing only within 64 times its box's average side: a dropped item (0.25) to 16 blocks, a chicken to 21,
 * an arrow to 32, a minecart or pig to 57, a zombie to 64, a spider to 79. Here everything draws to 64 at least, a
 * zombie's distance, and bigger things as far as beta drew them. A server sends items and arrows within 64 blocks, so
 * on a server they now show as far as it tells of them. Classes with a distance of their own (snowballs, eggs and the
 * fishing float at 64, fireballs at 256, lightning) keep it.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
	private static final double FLOOR = 64.0;

	@ModifyReturnValue(method = "shouldRender(D)Z", at = @At("RETURN"))
	private boolean oldfaithful$asFarAsAZombie(boolean beta, @Local(argsOnly = true) double distanceSquared) {
		return beta || distanceSquared < FLOOR * FLOOR;
	}
}
