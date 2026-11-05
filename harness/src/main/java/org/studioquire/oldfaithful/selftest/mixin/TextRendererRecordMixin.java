package org.studioquire.oldfaithful.selftest.mixin;

import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.LibraryScreens;

/** What a screen really drew, for the conflict harness: with {@code oldfaithful.selftest.recordText} only. */
@Mixin(TextRenderer.class)
abstract class TextRendererRecordMixin {
	@Inject(method = "draw(Ljava/lang/String;IIIZ)V", at = @At("HEAD"))
	private void oldfaithful$record(String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
		if (LibraryScreens.RECORD && text != null && LibraryScreens.drawn.size() < 4000) LibraryScreens.drawn.add(text);
	}
}
