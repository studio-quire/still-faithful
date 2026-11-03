package org.studioquire.oldfaithful.fixes.mixin.tpfall;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** #60: the fall a player has built up. */
@Mixin(Entity.class)
public interface EntityAccessor {
	@Accessor("fallDistance")
	void oldfaithful$setFallDistance(float fallDistance);
}
