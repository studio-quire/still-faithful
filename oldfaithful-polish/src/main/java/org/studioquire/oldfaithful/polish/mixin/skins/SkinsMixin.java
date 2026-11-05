package org.studioquire.oldfaithful.polish.mixin.skins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.studioquire.oldfaithful.polish.ModernSkin;

/** Slim arms, from the profile hardening has just read ({@link ModernSkin}). Without it every arm is wide. */
@Pseudo
@Mixin(targets = "org.studioquire.oldfaithful.hardening.skins.Skins")
abstract class SkinsMixin {
	@Inject(method = "parseProfile", at = @At("RETURN"), require = 0, remap = false)
	private static void oldfaithful$slimArms(String json, CallbackInfoReturnable<?> ci) {
		ModernSkin.profileRead(json);
	}
}
