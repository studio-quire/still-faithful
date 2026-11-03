package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** FixesCheck (#34, fishing): a fish on the hook. */
@Mixin(FishingBobberEntity.class)
public interface FishingBobberAccessor {
	@Accessor("hookCountdown")
	void oldfaithful$setHookCountdown(int ticks);
}
