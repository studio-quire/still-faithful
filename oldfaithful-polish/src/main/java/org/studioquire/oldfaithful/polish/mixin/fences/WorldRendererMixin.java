package org.studioquire.oldfaithful.polish.mixin.fences;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.FenceOutline;

/** #30: the outline of a fence aimed at is its post and arms ({@link FenceOutline}), not the block's box. */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@WrapOperation(method = "renderBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;renderOutline(Lnet/minecraft/util/math/Box;)V"))
	private void oldfaithful$fenceOutline(WorldRenderer renderer, Box box, Operation<Void> original, PlayerEntity player, HitResult hit,
			int i, ItemStack stack, float tickDelta) {
		if (player.world.getBlockId(hit.blockX, hit.blockY, hit.blockZ) != Block.FENCE.id) {
			original.call(renderer, box);
			return;
		}
		FenceOutline.draw(player.world, hit.blockX, hit.blockY, hit.blockZ,
				player.lastTickX + (player.x - player.lastTickX) * tickDelta,
				player.lastTickY + (player.y - player.lastTickY) * tickDelta,
				player.lastTickZ + (player.z - player.lastTickZ) * tickDelta);
	}
}
