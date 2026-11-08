package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether baseTick last found the entity in water; a change to true is a splash. */
@Mixin(Entity.class)
public interface EntityWaterAccessor {
	@Accessor("submergedInWater")
	boolean oldfaithful$submergedInWater();
}
