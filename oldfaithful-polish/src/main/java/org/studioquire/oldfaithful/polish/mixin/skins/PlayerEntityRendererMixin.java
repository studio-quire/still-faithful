package org.studioquire.oldfaithful.polish.mixin.skins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.ModernPlayerModel;
import org.studioquire.oldfaithful.polish.ModernSkin;

/**
 * One renderer draws every player with one model, so the model is changed for the player being drawn and put back. The
 * base class's {@code model} and this class's {@code bipedModel} are the same object in beta; both are set, so all of
 * it (the armour's flags, held items, capes, the inventory's figure) reads the one that's drawn.
 */
@Mixin(PlayerEntityRenderer.class)
abstract class PlayerEntityRendererMixin extends LivingEntityRenderer implements ModernPlayerModel.Wearer {
	@Shadow
	private BipedEntityModel bipedModel;
	@Unique
	private BipedEntityModel oldfaithful$betaModel;
	@Unique
	private ModernPlayerModel oldfaithful$wideModel, oldfaithful$slimModel;

	private PlayerEntityRendererMixin() {
		super(null, 0.0F);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void oldfaithful$models(CallbackInfo ci) {
		oldfaithful$betaModel = bipedModel;
		oldfaithful$wideModel = new ModernPlayerModel(false);
		oldfaithful$slimModel = new ModernPlayerModel(true);
	}

	@Override
	public void oldfaithful$wear(PlayerEntity player, TextureManager textures) {
		ModernSkin skin = ModernSkin.of(textures, player.skinUrl);
		bipedModel = skin == null ? oldfaithful$betaModel : skin.slim ? oldfaithful$slimModel : oldfaithful$wideModel;
		model = bipedModel;
	}

	@Override
	public void oldfaithful$wearBeta() {
		bipedModel = oldfaithful$betaModel;
		model = bipedModel;
	}

	@Inject(method = "render(Lnet/minecraft/entity/player/PlayerEntity;DDDFF)V", at = @At("HEAD"))
	private void oldfaithful$dress(PlayerEntity player, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
		oldfaithful$wear(player, dispatcher.textureManager);
	}

	@Inject(method = "render(Lnet/minecraft/entity/player/PlayerEntity;DDDFF)V", at = @At("RETURN"))
	private void oldfaithful$undress(PlayerEntity player, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
		oldfaithful$wearBeta();
	}

	@Inject(method = "renderHand", at = @At("HEAD"))
	private void oldfaithful$beforeHand(CallbackInfo ci) {
		if (bipedModel instanceof ModernPlayerModel) ((ModernPlayerModel) bipedModel).beginHand();
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void oldfaithful$sleeve(CallbackInfo ci) {
		if (bipedModel instanceof ModernPlayerModel) ((ModernPlayerModel) bipedModel).finishHand(0.0625F);
	}

	/** As later versions: what's held stays centred in a slim arm, half a pixel nearer the body. */
	@WrapOperation(method = "renderMore(Lnet/minecraft/entity/player/PlayerEntity;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPart;transform(F)V"))
	private void oldfaithful$slimGrip(ModelPart part, float scale, Operation<Void> original) {
		boolean slimArm = part == bipedModel.rightArm && bipedModel == oldfaithful$slimModel;
		if (slimArm) part.pivotX += 0.5F;
		original.call(part, scale);
		if (slimArm) part.pivotX -= 0.5F;
	}
}
