package org.studioquire.oldfaithful.fixes.mixin.seams;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.world.ChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.fixes.ChunkCorners;

/** #70: each chunk's list handed to its renderer with the chunk's corner, so it can be translated on its own. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@WrapOperation(method = "renderChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/world/ChunkRenderer;addGlList(I)V"))
	private void oldfaithful$addWithCorner(ChunkRenderer renderer, int list, Operation<Void> original, @Local ChunkBuilder chunk) {
		((ChunkCorners) renderer).oldfaithful$add(list, chunk.x, chunk.y, chunk.z);
	}
}
