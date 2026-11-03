package org.studioquire.oldfaithful.display.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.Sys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Paste (Ctrl+V in text fields) reads the clipboard through SDL, the game's window system now, instead of AWT's
 * toolkit, so the game needn't start AWT for it (and on macOS AWT can't share the main thread with SDL).
 */
@Mixin(Screen.class)
abstract class ScreenMixin {
	@Inject(method = "getClipboard", at = @At("HEAD"), cancellable = true)
	private static void oldfaithful$clipboardFromSdl(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(Sys.getClipboard());
	}
}
