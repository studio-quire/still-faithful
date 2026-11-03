package org.studioquire.oldfaithful.speed.mixin;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.chunk.ChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.ChunkMap;

/**
 * Singleplayer's chunk map through {@link ChunkMap}: the same Integer keys (ChunkPos.hashCode), hashed so they spread.
 * Only if the map is still vanilla's HashMap: a mod that put its own there (SmoothBeta does, and reads its own
 * field) keeps it.
 */
@Mixin(ChunkCache.class)
abstract class ChunkCacheMixin {
	@Shadow
	private Map chunkByPos;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void oldfaithful$chunkMap(CallbackInfo ci) {
		if (chunkByPos != null && chunkByPos.getClass() == HashMap.class && chunkByPos.isEmpty()) chunkByPos = new ChunkMap<>();
	}
}
