package org.studioquire.oldfaithful.speed.mixin;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.server.world.chunk.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.ChunkMap;

/**
 * The dedicated server's chunk map (Integer keys, ChunkPos.hashCode) through {@link ChunkMap}, as for
 * {@link ChunkCacheMixin}. Every block the server's light updates and decorations read or write looks its chunk up
 * here, and over half of its world generation went to these lookups. Its unload queue (a HashSet of the same keys)
 * stays vanilla's: the queue is drained in iteration order, and that order decides which chunks wait a tick longer.
 */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@Shadow
	private Map chunksByPos;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void oldfaithful$chunkMap(CallbackInfo ci) {
		if (chunksByPos != null && chunksByPos.getClass() == HashMap.class && chunksByPos.isEmpty()) chunksByPos = new ChunkMap<>();
	}
}
