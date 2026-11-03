package org.studioquire.oldfaithful.fixes.mixin.ridingmodels;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * #34, riding poses: a player is drawn with two armour models, one for the leggings and one for the rest, and the
 * riding pose and arm swing were set only on whichever of them the last piece of armour drawn had left selected. With
 * boots on, that's never the leggings' model, so a seated player's leggings stood upright. And the first-person hand
 * kept the riding pose of the last player drawn (yourself in third person, anyone in multiplayer), tilting the empty
 * hand. Both armour models now get the pose, and the hand is drawn unseated, as the other pose flags already were.
 */
@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin {
	@Shadow
	private BipedEntityModel armor1;
	@Shadow
	private BipedEntityModel armor2;
	@Shadow
	private BipedEntityModel bipedModel;

	@Inject(method = "render(Lnet/minecraft/entity/player/PlayerEntity;DDDFF)V", at = @At("HEAD"))
	private void oldfaithful$poseBothArmourModels(PlayerEntity player, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
		this.armor1.riding = this.armor2.riding = player.hasVehicle();
		this.armor1.handSwingProgress = this.armor2.handSwingProgress = player.getHandSwingProgress(tickDelta);
	}

	@Inject(method = "renderHand", at = @At("HEAD"))
	private void oldfaithful$handUnseated(CallbackInfo ci) {
		this.bipedModel.riding = false;
	}
}
