package org.studioquire.oldfaithful.polish.mixin.edition;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Edition;

/** Old Faithful's settings screen is titled "Still Faithful" ({@link Edition}); its group pages keep their titles. */
@Pseudo
@Mixin(targets = "org.studioquire.oldfaithful.options.settings.SettingsScreen")
abstract class SettingsScreenMixin {
	@Shadow(remap = false)
	protected String title;

	@Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
	private void oldfaithful$editionTitle(CallbackInfo ci) {
		this.title = Edition.NAME;
	}
}
