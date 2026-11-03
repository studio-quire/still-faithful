package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkCache;
import net.minecraft.world.chunk.ChunkSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.selftest.Bench;

/** Times terrain generation and decoration, for the benchmark. */
@Mixin(ChunkCache.class)
abstract class ChunkCacheBenchMixin {
	@WrapOperation(method = "loadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSource;getChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
	private Chunk oldfaithful$timeGenerate(ChunkSource generator, int x, int z, Operation<Chunk> original) {
		if (!Bench.ENABLED) return original.call(generator, x, z);
		long t = System.nanoTime();
		try {
			return original.call(generator, x, z);
		} finally {
			Bench.genNs += System.nanoTime() - t;
		}
	}

	@WrapOperation(method = "decorate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkSource;decorate(Lnet/minecraft/world/chunk/ChunkSource;II)V"))
	private void oldfaithful$timeDecorate(ChunkSource generator, ChunkSource source, int x, int z, Operation<Void> original) {
		if (!Bench.ENABLED) {
			original.call(generator, source, x, z);
			return;
		}
		long t = System.nanoTime();
		try {
			original.call(generator, source, x, z);
		} finally {
			Bench.genNs += System.nanoTime() - t;
		}
	}
}
