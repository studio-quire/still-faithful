package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface LedgeHuntEntityAccessor {
	@Accessor("fallDistance")
	void oldfaithful$setFallDistance(float distance);

	@org.spongepowered.asm.mixin.gen.Invoker("setBoundingBoxSpacing")
	void oldfaithful$setSize(float width, float height);
}
