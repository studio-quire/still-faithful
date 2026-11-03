package org.studioquire.oldfaithful.polish.mixin.autosave;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.server.world.chunk.ServerChunkCache;
import net.minecraft.world.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.BackgroundSaves;
import org.studioquire.oldfaithful.saves.RegionStorageAccess;

/** #14 ({@link BackgroundSaves}): a chunk loads only once its own queued save, if any, is written. A server. */
@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {
	@WrapOperation(method = "loadChunkFromStorage",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/ChunkStorage;loadChunk(Lnet/minecraft/world/World;II)Lnet/minecraft/world/chunk/Chunk;"))
	private Chunk oldfaithful$afterItsSave(ChunkStorage storage, World world, int x, int z, Operation<Chunk> original) {
		if (storage instanceof RegionStorageAccess) BackgroundSaves.awaitChunk(((RegionStorageAccess) storage).oldfaithful$dir(), x, z);
		return original.call(storage, world, x, z);
	}
}
