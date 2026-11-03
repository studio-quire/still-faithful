package org.studioquire.oldfaithful.speed.mixin;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(World.class)
public interface WorldAccessor {
	@Invoker("hasChunk")
	boolean oldfaithful$hasChunk(int chunkX, int chunkZ);
}
