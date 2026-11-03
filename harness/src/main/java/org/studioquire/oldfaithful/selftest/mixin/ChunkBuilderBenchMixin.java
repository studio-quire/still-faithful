package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.Bench;
import org.studioquire.oldfaithful.selftest.BenchChunk;

/** Counts render chunks rebuilt, and remembers which have been built since the renderer made them, for the benchmark. */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderBenchMixin implements BenchChunk {
	@Unique
	private boolean oldfaithful$built;

	@Override
	public boolean oldfaithful$built() {
		return oldfaithful$built;
	}

	@Inject(method = "rebuild", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;chunkUpdates:I", opcode = org.objectweb.asm.Opcodes.PUTSTATIC))
	private void oldfaithful$countRebuild(CallbackInfo ci) {
		if (!Bench.ENABLED) return;
		Bench.rebuilds++;
		oldfaithful$built = true;
	}
}
