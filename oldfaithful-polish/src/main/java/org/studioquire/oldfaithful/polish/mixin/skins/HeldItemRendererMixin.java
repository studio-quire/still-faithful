package org.studioquire.oldfaithful.polish.mixin.skins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.ModernPlayerModel;

/**
 * The hand is the only part of the player model drawn outside the renderer's render, and only this knows whose it is.
 * With the game's own texture manager: the dispatcher has none until it has drawn entities. Chosen before renderHand
 * is entered, so that what happens at its start (Old Faithful's riding fix) meets the model that's drawn.
 */
@Mixin(HeldItemRenderer.class)
abstract class HeldItemRendererMixin {
	@Shadow
	private Minecraft minecraft;

	@WrapOperation(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderHand()V"))
	private void oldfaithful$ownHand(PlayerEntityRenderer renderer, Operation<Void> original) {
		ModernPlayerModel.Wearer wearer = (ModernPlayerModel.Wearer) renderer;
		wearer.oldfaithful$wear(minecraft.player, minecraft.textureManager);
		original.call(renderer);
		wearer.oldfaithful$wearBeta();
	}
}
