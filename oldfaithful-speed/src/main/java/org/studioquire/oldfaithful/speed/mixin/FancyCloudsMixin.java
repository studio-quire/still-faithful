package org.studioquire.oldfaithful.speed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fancy clouds in one draw per pass instead of one per cell (36 cells, two passes: 72 draws from client memory each
 * frame, about 1 ms here).
 * <p>
 * Within a pass every cell is drawn with the same GL state, and every cell sets colour, normal and texture, so the
 * tessellator's client state is the same for the batch as for each cell. Batching gives the same vertices in the same
 * order in one glDrawArrays: the same primitives, so the same pixels. Each cell adds whole quads, so the
 * tessellator's quad-to-triangle conversion stays aligned. Normals of the triangles' duplicated vertices aren't
 * copied by the tessellator in vanilla either (they're whatever the buffer held); lighting is off for clouds, so
 * normals don't reach the pixels.
 * <p>
 * The batch opens at the first cell's startQuads and is drawn just before the next pass changes the colour mask, and
 * after the last pass at the colour reset that ends the method: the same state each cell's draw had.
 */
@Mixin(WorldRenderer.class)
abstract class FancyCloudsMixin {
	@Unique
	private boolean oldfaithful$batchOpen;
	@Unique
	private Operation<Void> oldfaithful$draw;

	@Inject(method = "renderFancyClouds", at = @At("HEAD"))
	private void oldfaithful$reset(float tickDelta, CallbackInfo ci) {
		// Left open only if the batch's draw point didn't apply: close it rather than leave the tessellator stuck.
		if (oldfaithful$batchOpen && ((TessellatorAccessor) Tessellator.INSTANCE).oldfaithful$drawing()) Tessellator.INSTANCE.draw();
		oldfaithful$batchOpen = false;
		oldfaithful$draw = null;
	}

	@WrapOperation(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;startQuads()V"))
	private void oldfaithful$startOnce(Tessellator tessellator, Operation<Void> original) {
		// Still open from an earlier cell (if vanilla's draw ran instead of ours, it isn't, and this cell starts anew).
		if (oldfaithful$batchOpen && ((TessellatorAccessor) tessellator).oldfaithful$drawing()) return;
		original.call(tessellator);
		oldfaithful$batchOpen = true;
	}

	@WrapOperation(method = "renderFancyClouds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;draw()V"))
	private void oldfaithful$drawLater(Tessellator tessellator, Operation<Void> original) {
		if (!oldfaithful$batchOpen) {
			original.call(tessellator); // the batch's start didn't apply: each cell draws as in vanilla
			return;
		}
		oldfaithful$draw = original;
	}

	@Inject(method = "renderFancyClouds", at = {
			@At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColorMask(ZZZZ)V", remap = false),
			@At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", remap = false)})
	private void oldfaithful$drawBatch(float tickDelta, CallbackInfo ci) {
		if (!oldfaithful$batchOpen) return;
		Operation<Void> draw = oldfaithful$draw;
		oldfaithful$batchOpen = false;
		oldfaithful$draw = null;
		if (draw != null) draw.call(Tessellator.INSTANCE);
		else Tessellator.INSTANCE.draw();
	}
}
