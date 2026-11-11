package org.studioquire.oldfaithful.polish.mixin.boatsteering;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Boat Steering in single player: the rider's own movement input, as its keys set it this tick. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("forwardSpeed")
	float oldfaithful$forwardSpeed();

	@Accessor("sidewaysSpeed")
	float oldfaithful$sidewaysSpeed();
}
