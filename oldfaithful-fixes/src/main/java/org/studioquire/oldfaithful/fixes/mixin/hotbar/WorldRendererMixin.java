package org.studioquire.oldfaithful.fixes.mixin.hotbar;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #34, the white hotbar: drawing the cracks on the targeted block sets additive blending and leaves it set. Whatever
 * draws next with blending usually sets its own (the block outline, clouds, rain, Fancy's vignette), but with Fast
 * graphics in the Nether while aiming at a mob none of them runs, and the hotbar drew additively: white. The usual
 * blending is put back when the cracks are done.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
	@Inject(method = "renderMiningProgress", at = @At("TAIL"))
	private void oldfaithful$restoreBlend(PlayerEntity player, HitResult hit, int pass, ItemStack hand, float tickDelta, CallbackInfo ci) {
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}
}
