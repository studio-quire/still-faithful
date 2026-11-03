package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** MultiplayerCheck (#15): whether a remote mob has finished moving towards the server's last position. */
@Mixin(LivingEntity.class)
public interface LivingEntityLerpAccessor {
	@Accessor("bodyTrackingIncrements")
	int oldfaithful$lerpSteps();
}
