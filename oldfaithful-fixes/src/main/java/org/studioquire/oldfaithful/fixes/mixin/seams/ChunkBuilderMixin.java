package org.studioquire.oldfaithful.fixes.mixin.seams;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * #70: a chunk's display list no longer translates by the chunk's place in its 1024-block region; the chunk is
 * translated once when drawn instead (ChunkRendererMixin, ChunkTransform). The list's scale about its middle stays.
 */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderMixin {
	@WrapOperation(method = "rebuild", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;translateToRenderPosition()V"))
	private void oldfaithful$noRegionTranslation(ChunkBuilder chunk, Operation<Void> original) {
	}
}
