package org.studioquire.oldfaithful.polish.selftest.mixin;

import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkCache.class)
public interface ChunkCacheStorage {
	@Accessor("storage")
	ChunkStorage oldfaithful$storage();
}
