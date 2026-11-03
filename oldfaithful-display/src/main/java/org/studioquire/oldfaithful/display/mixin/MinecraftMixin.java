package org.studioquire.oldfaithful.display.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.display.FrameClock;
import org.studioquire.oldfaithful.display.WindowPlacement;
import org.studioquire.oldfaithful.display.WindowTitle;
import org.studioquire.oldfaithful.lwjgl.Settings;

/**
 * The game's window on our LWJGL. b1.7.3 without a canvas never resized (its size checks were all on the canvas) and
 * made fullscreen by switching the display mode, then sized the window to the desktop on the way back out. Here:
 * <ul>
 * <li>The title is the launcher frame's ("Minecraft"), as players saw it.</li>
 * <li>The window is resizable, and each frame the game takes the window's framebuffer size, as it took the canvas's:
 * resizing, moving to a display with another scale and fullscreen all come through this. It takes it once right
 * after the window opens too, so the loading screen fits a window that opened at another size than the game's
 * (the launcher's size, maximised, scaled to the display).</li>
 * <li>Where the window opens (the launcher's window settings, or where it was) is WindowPlacement's; as the game
 * stops, the window's placement is kept there if remembering is on, fullscreen or not; a game that stopped fullscreen
 * opens fullscreen again, over its kept window.</li>
 * <li>F11's fullscreen leaves the display mode alone (borderless, or exclusive at the desktop resolution) and the
 * window keeps its own size for when it comes back.</li>
 * <li>Losing focus no longer drops borderless fullscreen (it would on every alt-tab); exclusive still does, as
 * before, since an exclusive mode can't sit behind other windows.</li>
 * </ul>
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow public int displayWidth;
	@Shadow public int displayHeight;
	@Shadow private boolean fullscreen;

	@Shadow
	private void resize(int width, int height) {
	}

	/** Each pass of the game loop fixes its instant and takes the mouse motion up to it (FrameClock has why). */
	@Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Box;resetCacheCount()V"))
	private void oldfaithful$frameStart(CallbackInfo ci) {
		FrameClock.frameStart();
	}

	@Inject(method = "init", at = @At("HEAD"))
	private void oldfaithful$resizableWindow(CallbackInfo ci) {
		Display.setResizable(true);
	}

	@Inject(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;create()V", shift = At.Shift.AFTER, remap = false))
	private void oldfaithful$sizeFromWindow(CallbackInfo ci) {
		// Fullscreen when it last stopped (window.remember): as F11 would, over the window just opened where it was,
		// so the game's own flag agrees and one F11 brings that window back.
		if (WindowPlacement.startFullscreen() && !fullscreen) {
			try {
				Display.setFullscreen(true);
				fullscreen = true;
			} catch (LWJGLException e) {
				System.out.println("[purefaithful-display] WARN Could not open fullscreen as the game was when it stopped: " + e);
			}
		}
		oldfaithful$matchFramebuffer();
	}

	@Inject(method = "stop", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;destroy()V", remap = false))
	private void oldfaithful$keepPlacement(CallbackInfo ci) {
		WindowPlacement.save();
	}

	/** The window shows the title LWJGL 2's player saw (the launcher frame's), not the game's own doubled one. */
	@WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setTitle(Ljava/lang/String;)V", remap = false))
	private void oldfaithful$frameTitle(String title, Operation<Void> original) {
		original.call(WindowTitle.get());
	}

	@Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleScreenshotKey()V", shift = At.Shift.AFTER))
	private void oldfaithful$followWindowSize(CallbackInfo ci) {
		oldfaithful$matchFramebuffer();
	}

	@WrapOperation(method = "toggleFullscreen", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setDisplayMode(Lorg/lwjgl/opengl/DisplayMode;)V", remap = false))
	private void oldfaithful$keepWindowSize(DisplayMode mode, Operation<Void> original) {
	}

	@Inject(method = "toggleFullscreen", at = @At("TAIL"))
	private void oldfaithful$sizeAfterToggle(CallbackInfo ci) {
		oldfaithful$matchFramebuffer();
	}

	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;toggleFullscreen()V"))
	private void oldfaithful$stayFullscreenUnfocused(Minecraft minecraft, Operation<Void> original) {
		if (Settings.exclusiveFullscreen) original.call(minecraft);
	}

	private void oldfaithful$matchFramebuffer() {
		int w = Display.getWidth(), h = Display.getHeight();
		if (w > 0 && h > 0 && (w != displayWidth || h != displayHeight)) resize(w, h);
	}
}
