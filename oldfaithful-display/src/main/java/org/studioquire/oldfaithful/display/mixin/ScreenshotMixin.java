package org.studioquire.oldfaithful.display.mixin;

import java.io.File;
import java.nio.ByteBuffer;

import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Beta's own bug (docs/bugs.md, screenshots after enlarging the window): take() keeps its pixel buffer while its
 * capacity is at least width * height, but reads width * height * 3 bytes into it, so once the window has grown the
 * read overruns and the screenshot fails. Our resizable window makes that common, so both editions fix it: drop a
 * buffer too small for the read and let take() allocate one as it always does.
 */
@Mixin(Screenshot.class)
abstract class ScreenshotMixin {
	@Shadow
	private static ByteBuffer pixels;

	@Inject(method = "take", at = @At("HEAD"))
	private static void oldfaithful$bufferFitsRead(File gameDir, int width, int height, CallbackInfoReturnable<String> cir) {
		if (pixels != null && pixels.capacity() < width * height * 3) pixels = null;
	}
}
