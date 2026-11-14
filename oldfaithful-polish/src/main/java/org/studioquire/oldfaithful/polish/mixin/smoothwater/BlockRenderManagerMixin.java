package org.studioquire.oldfaithful.polish.mixin.smoothwater;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.SmoothWater;

/**
 * Smooth water (SmoothWater): renderFluid draws as beta's, each face's geometry and texture untouched, and each vertex
 * gets its corner's light just before it's written. A face is known by its light reading (beta reads the top at the
 * fluid's cell, the bottom at the cell below, a side at the cell beside it, each just before that face is drawn), and a
 * vertex's corner by where it is. The bottom goes through renderBottomFace, which takes per-corner colours as
 * renderSmooth gives them. Without Smooth Lighting, or for lava, every call goes through as beta made it.
 */
@Mixin(BlockRenderManager.class)
abstract class BlockRenderManagerMixin {
	@Unique
	private static final int NONE = 0, TOP = 1, SIDE = 2;

	@Shadow
	private BlockView blockView;
	@Shadow
	private boolean useAo;
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

	/** The fluid being drawn, while it's lit smoothly; null otherwise. */
	@Unique
	private Block oldfaithful$fluid;
	@Unique
	private int oldfaithful$x, oldfaithful$y, oldfaithful$z;
	@Unique
	private float oldfaithful$red, oldfaithful$green, oldfaithful$blue;
	/** The face being drawn: NONE, TOP or SIDE, and a side's direction and shade. */
	@Unique
	private int oldfaithful$face, oldfaithful$dx, oldfaithful$dz;
	@Unique
	private float oldfaithful$shade;
	@Unique
	private final float[] oldfaithful$light = new float[4];

	@Inject(method = "renderFluid", at = @At("HEAD"))
	private void oldfaithful$begin(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
		oldfaithful$face = NONE;
		if (!SmoothWater.applies(block)) {
			oldfaithful$fluid = null;
			return;
		}
		oldfaithful$fluid = block;
		oldfaithful$x = x;
		oldfaithful$y = y;
		oldfaithful$z = z;
		int tint = block.getColorMultiplier(blockView, x, y, z);
		oldfaithful$red = (tint >> 16 & 0xFF) / 255.0F;
		oldfaithful$green = (tint >> 8 & 0xFF) / 255.0F;
		oldfaithful$blue = (tint & 0xFF) / 255.0F;
	}

	@WrapOperation(method = "renderFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getLuminance(Lnet/minecraft/world/BlockView;III)F"))
	private float oldfaithful$face(Block block, BlockView view, int x, int y, int z, Operation<Float> original) {
		float flat = original.call(block, view, x, y, z);
		Block fluid = oldfaithful$fluid;
		if (fluid == null) return flat;
		int dx = x - oldfaithful$x, dy = y - oldfaithful$y, dz = z - oldfaithful$z;
		oldfaithful$face = NONE;
		if (dy == 0 && dx == 0 && dz == 0) {
			SmoothWater.top(fluid, view, oldfaithful$x, oldfaithful$y, oldfaithful$z, oldfaithful$light);
			oldfaithful$face = TOP;
			oldfaithful$shade = 1.0F;
		} else if (dy == 0 && Math.abs(dx) + Math.abs(dz) == 1) {
			SmoothWater.side(fluid, view, oldfaithful$x, oldfaithful$y, oldfaithful$z, dx, dz, oldfaithful$light);
			oldfaithful$face = SIDE;
			oldfaithful$dx = dx;
			oldfaithful$dz = dz;
			// Beta's shades: 0.8 facing north or south, 0.6 east or west.
			oldfaithful$shade = dz != 0 ? 0.8F : 0.6F;
		}
		return flat;
	}

	@WrapOperation(method = "renderFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Tessellator;vertex(DDDDD)V"))
	private void oldfaithful$vertex(Tessellator tessellator, double x, double y, double z, double u, double v, Operation<Void> original) {
		if (oldfaithful$fluid != null && oldfaithful$face != NONE) {
			boolean highX = x > oldfaithful$x + 0.5, highZ = z > oldfaithful$z + 0.5;
			int corner;
			if (oldfaithful$face == TOP) corner = SmoothWater.corner(highX, highZ);
			else corner = SmoothWater.corner(oldfaithful$dz != 0 ? highX : highZ, y > oldfaithful$y);
			float light = oldfaithful$shade * oldfaithful$light[corner];
			tessellator.color(light * oldfaithful$red, light * oldfaithful$green, light * oldfaithful$blue);
		}
		original.call(tessellator, x, y, z, u, v);
	}

	@WrapOperation(method = "renderFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBottomFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$bottom(BlockRenderManager renderer, Block block, double x, double y, double z, int texture, Operation<Void> original) {
		Block fluid = oldfaithful$fluid;
		if (fluid == null) {
			original.call(renderer, block, x, y, z, texture);
			return;
		}
		float[] light = oldfaithful$light;
		SmoothWater.bottom(fluid, blockView, oldfaithful$x, oldfaithful$y, oldfaithful$z, light);
		// renderBottomFace's corners in order: (x, z + 1), (x, z), (x + 1, z), (x + 1, z + 1); 0.5 and untinted, as beta's.
		firstVertexRed = firstVertexGreen = firstVertexBlue = 0.5F * light[SmoothWater.corner(false, true)];
		secondVertexRed = secondVertexGreen = secondVertexBlue = 0.5F * light[SmoothWater.corner(false, false)];
		thirdVertexRed = thirdVertexGreen = thirdVertexBlue = 0.5F * light[SmoothWater.corner(true, false)];
		fourthVertexRed = fourthVertexGreen = fourthVertexBlue = 0.5F * light[SmoothWater.corner(true, true)];
		boolean was = useAo;
		useAo = true;
		try {
			original.call(renderer, block, x, y, z, texture);
		} finally {
			useAo = was;
		}
	}
}
