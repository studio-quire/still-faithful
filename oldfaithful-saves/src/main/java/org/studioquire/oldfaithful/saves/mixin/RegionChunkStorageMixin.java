package org.studioquire.oldfaithful.saves.mixin;

import java.io.File;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.RegionChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.saves.ChunkLoad;
import org.studioquire.oldfaithful.saves.RegionStorageAccess;

/** Chunks load through {@link ChunkLoad}; one whose record couldn't be read at all is never saved over. */
@Mixin(RegionChunkStorage.class)
abstract class RegionChunkStorageMixin implements RegionStorageAccess {
	@Shadow
	@Final
	private File dir;

	@Override
	public File oldfaithful$dir() {
		return dir;
	}

	@Inject(method = "loadChunk", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$load(World world, int chunkX, int chunkZ, CallbackInfoReturnable<Chunk> cir) {
		ChunkLoad.Result result = ChunkLoad.load(dir, world, chunkX, chunkZ);
		if (result.handled) cir.setReturnValue(result.chunk);
	}

	@Inject(method = "saveChunk", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$save(World world, Chunk chunk, CallbackInfo ci) {
		if (ChunkLoad.isProtected(dir, chunk.x, chunk.z)) ci.cancel();
	}
}
