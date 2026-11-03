package org.studioquire.oldfaithful.speed.mixin;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.world.chunk.MultiplayerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.speed.ChunkMap;

/** Multiplayer's chunk map (ChunkPos keys, the same hashCode) through {@link ChunkMap}, as for {@link ChunkCacheMixin}. */
@Mixin(MultiplayerChunkCache.class)
abstract class MultiplayerChunkCacheMixin {
	@Shadow
	private Map chunksByPos;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void oldfaithful$chunkMap(CallbackInfo ci) {
		if (chunksByPos != null && chunksByPos.getClass() == HashMap.class && chunksByPos.isEmpty()) chunksByPos = new ChunkMap<>();
	}
}
