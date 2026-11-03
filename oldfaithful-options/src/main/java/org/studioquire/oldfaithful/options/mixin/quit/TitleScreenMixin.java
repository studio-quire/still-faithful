package org.studioquire.oldfaithful.options.mixin.quit;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Quit Game on the title screen. Beta has the button already, beside a half-width Options, but shows it only when the
 * game runs on its own; in a launcher it ran as an applet (Fabric starts it as one too), whose page or frame was there
 * to close, so it was hidden. With our own window, in fullscreen especially, there's nothing else to close. Beta's own
 * button, layout and action (scheduleStop, as the window's close button does): only this screen's reading of the
 * game's isApplet flag changes, not the flag.
 */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin {
	@ModifyExpressionValue(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;isApplet:Z"))
	private boolean oldfaithful$standAlone(boolean isApplet) {
		return false;
	}
}
