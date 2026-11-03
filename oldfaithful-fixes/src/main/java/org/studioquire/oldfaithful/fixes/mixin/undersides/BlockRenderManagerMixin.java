package org.studioquire.oldfaithful.fixes.mixin.undersides;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * #30, the dark undersides: a face of a block shape that lies inside the block's own space (a fence's bars, whose
 * undersides are well above the ground) should take the light of that space. Flat lighting does that for the top and
 * the four sides, and the door renderer for its underside too, but the underside always took the light of the block
 * below: on a fence standing on the ground, the solid block's, which is none, so the bars' undersides drew black.
 * Smooth lighting took it from below as well. Both now light an underside above the block's floor from the block's own
 * space. Faces on the block's edge are unchanged, so only shapes with a raised underside look different: fence bars,
 * and the inner faces of pistons pushing down.
 */
@Mixin(BlockRenderManager.class)
abstract class BlockRenderManagerMixin {
	@Shadow
	private int textureOverride;
	@Shadow
	private float selfBrightness;
	@Shadow
	private float firstVertexRed;
	@Shadow
	private float secondVertexRed;
	@Shadow
	private float thirdVertexRed;
	@Shadow
	private float fourthVertexRed;
	@Shadow
	private float firstVertexGreen;
	@Shadow
	private float secondVertexGreen;
	@Shadow
	private float thirdVertexGreen;
	@Shadow
	private float fourthVertexGreen;
	@Shadow
	private float firstVertexBlue;
	@Shadow
	private float secondVertexBlue;
	@Shadow
	private float thirdVertexBlue;
	@Shadow
	private float fourthVertexBlue;

	// Flat lighting: the second light lookup is the underside's, at the block below.
	@WrapOperation(method = "renderFlat", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getLuminance(Lnet/minecraft/world/BlockView;III)F", ordinal = 1))
	private float oldfaithful$flatUnderside(Block block, BlockView view, int x, int y, int z, Operation<Float> original) {
		return original.call(block, view, x, block.minY > 0.0 ? y + 1 : y, z);
	}

	// Smooth lighting: the underside's four corners are set just before it's drawn.
	@Inject(method = "renderSmooth", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBottomFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$smoothUnderside(Block block, int x, int y, int z, float red, float green, float blue, CallbackInfoReturnable<Boolean> cir) {
		if (block.minY <= 0.0) return;
		// As the method's own base colour for this face: untinted for texture 3 (grass's side) or an override.
		boolean tint = block.textureId != 3 && this.textureOverride < 0;
		float light = this.selfBrightness * 0.5F;
		float r = (tint ? red : 1.0F) * light;
		float g = (tint ? green : 1.0F) * light;
		float b = (tint ? blue : 1.0F) * light;
		this.firstVertexRed = this.secondVertexRed = this.thirdVertexRed = this.fourthVertexRed = r;
		this.firstVertexGreen = this.secondVertexGreen = this.thirdVertexGreen = this.fourthVertexGreen = g;
		this.firstVertexBlue = this.secondVertexBlue = this.thirdVertexBlue = this.fourthVertexBlue = b;
	}
}
