package org.studioquire.oldfaithful.selftest.mixin;

import java.util.List;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface WorldLightQueueAccessor {
	/** Light updates still waiting to be worked through. */
	@Accessor("lightingQueue")
	List<?> oldfaithful$lightingQueue();
}
