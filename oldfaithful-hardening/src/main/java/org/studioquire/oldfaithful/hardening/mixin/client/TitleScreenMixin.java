package org.studioquire.oldfaithful.hardening.mixin.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.hardening.Branding;

/** "Pure Faithful <version>" (or an edition's name, {@link Branding}) under "Minecraft Beta 1.7.3" in the title screen's corner, drawn as beta draws that line. */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
	@Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
	private void oldfaithful$mark(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		drawTextWithShadow(textRenderer, Branding.TEXT, 2, 12, Branding.COLOUR);
	}
}
