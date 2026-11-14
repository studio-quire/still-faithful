package org.studioquire.oldfaithful.polish.mixin.fencejoins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.FenceJoins;

/**
 * Fence Joins: renderFence draws an arm toward each side whose block has the fence's own id, reading the four ids
 * beside it (twice over). A block the fence joins ({@link FenceJoins}) reads as the fence's id there, so beta's own
 * code draws the arms, as it draws them to fences.
 */
@Mixin(BlockRenderManager.class)
abstract class BlockRenderManagerMixin {
	@WrapOperation(method = "renderFence", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BlockView;getBlockId(III)I"))
	private int oldfaithful$joinBlocks(BlockView view, int x, int y, int z, Operation<Integer> original, Block fence, int fx, int fy, int fz) {
		int id = original.call(view, x, y, z);
		return id != fence.id && FenceJoins.joins(view, fence, fx, fy, fz, x - fx, z - fz) ? fence.id : id;
	}
}
