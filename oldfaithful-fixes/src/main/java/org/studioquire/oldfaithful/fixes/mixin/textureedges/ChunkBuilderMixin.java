package org.studioquire.oldfaithful.fixes.mixin.textureedges;

import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.TextureEdges;

/** #72: the texture edge inset applies to what a chunk build writes, nothing else the tessellator draws. */
@Mixin(ChunkBuilder.class)
abstract class ChunkBuilderMixin {
	@Inject(method = "rebuild", at = @At("HEAD"))
	private void oldfaithful$start(CallbackInfo ci) {
		TextureEdges.building = true;
	}

	@Inject(method = "rebuild", at = @At("RETURN"))
	private void oldfaithful$end(CallbackInfo ci) {
		TextureEdges.building = false;
	}
}
