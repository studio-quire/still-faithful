package org.studioquire.oldfaithful.polish.mixin.edition;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.studioquire.oldfaithful.polish.Edition;

/** The title screen's edition line reads "Still Faithful <version>" ({@link Edition}). */
@Pseudo
@Mixin(targets = "org.studioquire.oldfaithful.hardening.Branding")
abstract class BrandingMixin {
	@ModifyReturnValue(method = "text", at = @At("RETURN"), require = 0, remap = false)
	private static String oldfaithful$stillFaithful(String line) {
		return Edition.titleLine();
	}
}
