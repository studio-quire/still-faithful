package org.studioquire.oldfaithful.polish.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.studioquire.oldfaithful.polish.selftest.DuskCheck;

/** For DuskCheck: how long building a chunk's mesh takes, with each lighting. */
@Mixin(ChunkBuilder.class)
abstract class DuskChunkBuilderMixin {
	@Shadow
	public boolean dirty;

	@WrapMethod(method = "rebuild")
	private void oldfaithful$time(Operation<Void> original) {
		if (!dirty) {
			original.call();
			return;
		}
		long t = System.nanoTime();
		original.call();
		DuskCheck.rebuilt((ChunkBuilder) (Object) this, System.nanoTime() - t);
	}
}
