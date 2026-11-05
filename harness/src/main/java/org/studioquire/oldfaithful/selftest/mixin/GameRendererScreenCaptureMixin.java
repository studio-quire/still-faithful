package org.studioquire.oldfaithful.selftest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.selftest.ScreenCapture;

/**
 * For the settings check (SettingsCheck): a screen drawn with the mouse where the test says (the hidden window has
 * no real pointer), and its pixels read back once drawn; with no screen open, the frame's world and HUD. Does nothing
 * unless a capture was asked for.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererScreenCaptureMixin {
	@WrapOperation(method = "onFrameUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
	private void oldfaithful$capture(Screen screen, int mouseX, int mouseY, float delta, Operation<Void> original) {
		ScreenCapture capture = ScreenCapture.pending;
		if (capture == null) {
			original.call(screen, mouseX, mouseY, delta);
			return;
		}
		original.call(screen, capture.mouseX, capture.mouseY, delta);
		capture.take();
	}

	@Inject(method = "onFrameUpdate", at = @At("TAIL"))
	private void oldfaithful$captureWorld(float delta, CallbackInfo ci) {
		ScreenCapture capture = ScreenCapture.pending;
		if (capture != null) capture.take();
	}
}
