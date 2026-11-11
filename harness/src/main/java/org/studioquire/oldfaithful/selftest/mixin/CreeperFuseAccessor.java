package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** For CullingMeasure: a creeper swollen as it is about to burst. */
@Mixin(CreeperEntity.class)
public interface CreeperFuseAccessor {
	@Accessor("fuseTime")
	void oldfaithful$setFuseTime(int ticks);

	@Accessor("lastFuseTime")
	void oldfaithful$setLastFuseTime(int ticks);
}
