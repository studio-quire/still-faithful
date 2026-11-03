package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.StartupCheck;

/** Lets the start-up test read the title screen's pixels once it's drawn (the Pure Faithful line in its corner). */
@Mixin(TitleScreen.class)
abstract class TitleScreenStartupMixin extends Screen {
	@Inject(method = "render", at = @At("TAIL"))
	private void oldfaithful$titleDrawn(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (StartupCheck.ENABLED) StartupCheck.titleDrawn(width);
	}
}
