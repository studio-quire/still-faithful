package org.studioquire.oldfaithful.settings.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.settings.LibrarySelection;

/** Mods the library's list meant to load and couldn't, said where a player looks before going into a world. */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
	@Inject(method = "render", at = @At("TAIL"))
	private void oldfaithful$libraryNotice(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		String notice = LibrarySelection.notice();
		if (notice != null) drawCenteredTextWithShadow(textRenderer, notice, width / 2, 2, 0xFFFF55);
	}
}
