package org.studioquire.oldfaithful.polish.mixin.smoothcactus;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.SmoothCactus;

/**
 * Smooth cactus (SmoothCactus): renderCactus draws as beta's, its faces, texture and the pixel its sides are drawn in
 * untouched, and each face's call to renderXFace goes through the per-vertex colours renderSmooth uses (useAo), its four
 * corners' light worked out first, times the tint and shade beta gave the face. Without Smooth Lighting every call goes
 * through as beta made it.
 */
@Mixin(BlockRenderManager.class)
abstract class BlockRenderManagerMixin {
	@Unique
	private static final String CACTUS = "renderCactus(Lnet/minecraft/block/Block;IIIFFF)Z";

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

	@Unique
	private boolean oldfaithful$smoothCactus;
	@Unique
	private float oldfaithful$cactusRed, oldfaithful$cactusGreen, oldfaithful$cactusBlue;
	@Unique
	private final float[] oldfaithful$cactusLight = new float[4];

	@Inject(method = CACTUS, at = @At("HEAD"))
	private void oldfaithful$cactus(Block block, int x, int y, int z, float red, float green, float blue, CallbackInfoReturnable<Boolean> cir) {
		oldfaithful$smoothCactus = SmoothCactus.on();
		oldfaithful$cactusRed = red;
		oldfaithful$cactusGreen = green;
		oldfaithful$cactusBlue = blue;
	}

	@WrapOperation(method = CACTUS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBottomFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$cactusBottom(BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		oldfaithful$cactusFace(0, r, b, x, y, z, texture, original);
	}

	@WrapOperation(method = CACTUS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderTopFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$cactusTop(BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		oldfaithful$cactusFace(1, r, b, x, y, z, texture, original);
	}

	@WrapOperation(method = CACTUS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderEastFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$cactusEast(BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		oldfaithful$cactusFace(2, r, b, x, y, z, texture, original);
	}

	@WrapOperation(method = CACTUS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderWestFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$cactusWest(BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		oldfaithful$cactusFace(3, r, b, x, y, z, texture, original);
	}

	@WrapOperation(method = CACTUS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderNorthFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$cactusNorth(BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		oldfaithful$cactusFace(4, r, b, x, y, z, texture, original);
	}

	@WrapOperation(method = CACTUS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderSouthFace(Lnet/minecraft/block/Block;DDDI)V"))
	private void oldfaithful$cactusSouth(BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		oldfaithful$cactusFace(5, r, b, x, y, z, texture, original);
	}

	@Unique
	private void oldfaithful$cactusFace(int side, BlockRenderManager r, Block b, double x, double y, double z, int texture, Operation<Void> original) {
		if (!oldfaithful$smoothCactus) {
			original.call(r, b, x, y, z, texture);
			return;
		}
		float[] light = oldfaithful$cactusLight;
		SmoothCactus.face(b, blockView, (int) x, (int) y, (int) z, side, light);
		float shade = SmoothCactus.SHADE[side];
		float red = shade * oldfaithful$cactusRed, green = shade * oldfaithful$cactusGreen, blue = shade * oldfaithful$cactusBlue;
		firstVertexRed = red * light[0];
		firstVertexGreen = green * light[0];
		firstVertexBlue = blue * light[0];
		secondVertexRed = red * light[1];
		secondVertexGreen = green * light[1];
		secondVertexBlue = blue * light[1];
		thirdVertexRed = red * light[2];
		thirdVertexGreen = green * light[2];
		thirdVertexBlue = blue * light[2];
		fourthVertexRed = red * light[3];
		fourthVertexGreen = green * light[3];
		fourthVertexBlue = blue * light[3];
		boolean was = useAo;
		useAo = true;
		try {
			original.call(r, b, x, y, z, texture);
		} finally {
			useAo = was;
		}
	}
}
