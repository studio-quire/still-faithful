package org.studioquire.oldfaithful.polish.mixin.edition;

import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.polish.Edition;

/** Old Faithful's settings button in Options reads "Still Faithful..." ({@link Edition}); after the options mod adds it. */
@Mixin(value = OptionsScreen.class, priority = 1100)
abstract class OptionsScreenMixin extends Screen {
	@Inject(method = "init", at = @At("TAIL"))
	private void oldfaithful$editionButton(CallbackInfo ci) {
		for (Object o : (List<?>) this.buttons) {
			ButtonWidget b = (ButtonWidget) o;
			if ("Old Faithful...".equals(b.text)) b.text = Edition.NAME + "...";
		}
	}
}
