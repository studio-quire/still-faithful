package org.studioquire.oldfaithful.fixes.mixin.sliders;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.Option;
import net.minecraft.client.util.ScreenScaler;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.fixes.SliderSteps;

/**
 * #40 ({@link SliderSteps}): the game's own sliders follow the mouse to the screen pixel rather than the whole GUI unit,
 * land on whole labelled steps, and draw their knob on the pixel the value falls in.
 */
@Mixin(SliderWidget.class)
abstract class SliderWidgetMixin extends ButtonWidget {
	@Shadow
	public float value;
	@Shadow
	private Option option;

	SliderWidgetMixin() {
		super(0, 0, 0, null);
	}

	/** The labelled steps of the game's own sliders; 0 for any other (a mod's), which is left alone. */
	@Unique
	private int oldfaithful$steps() {
		if (this.option == Option.SENSITIVITY) return 200;
		if (this.option == Option.MUSIC || this.option == Option.SOUND) return 100;
		return 0;
	}

	@Unique
	private static int oldfaithful$scale(Minecraft minecraft) {
		return new ScreenScaler(minecraft.options, minecraft.displayWidth, minecraft.displayHeight).scaleFactor;
	}

	/** The value for the mouse at {@code pixelX}, snapped; {@code raw} (beta's, from the whole-unit position) for other sliders. */
	@Unique
	private float oldfaithful$at(Minecraft minecraft, float raw, int pixelX, int mouseX) {
		int steps = oldfaithful$steps();
		if (steps == 0) return raw;
		float x = SliderSteps.pointerX(pixelX, oldfaithful$scale(minecraft), mouseX);
		this.value = SliderSteps.snap(SliderSteps.position(x, this.x + 4), steps);
		return this.value;
	}

	// Dragging sets the option from the mouse where it is now, as the frame's mouseX was worked out.
	@ModifyArg(method = "renderBackground",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;setFloat(Lnet/minecraft/client/option/Option;F)V"), index = 1)
	private float oldfaithful$dragged(float raw, @Local(argsOnly = true) Minecraft minecraft, @Local(argsOnly = true, ordinal = 0) int mouseX) {
		return oldfaithful$at(minecraft, raw, Mouse.getX(), mouseX);
	}

	// A click sets it from the click's event, as the screen worked out mouseX.
	@ModifyArg(method = "isMouseOver",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;setFloat(Lnet/minecraft/client/option/Option;F)V"), index = 1)
	private float oldfaithful$clicked(float raw, @Local(argsOnly = true) Minecraft minecraft, @Local(argsOnly = true, ordinal = 0) int mouseX) {
		return oldfaithful$at(minecraft, raw, Mouse.getEventX(), mouseX);
	}

	@Inject(method = "renderBackground", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/widget/SliderWidget;drawTexture(IIIIII)V"))
	private void oldfaithful$knobOnItsPixel(Minecraft minecraft, int mouseX, int mouseY, CallbackInfo ci) {
		GL11.glPushMatrix();
		if (oldfaithful$steps() != 0) GL11.glTranslatef(SliderSteps.knobOffset(this.value, oldfaithful$scale(minecraft)), 0.0F, 0.0F);
	}

	@Inject(method = "renderBackground", at = @At(value = "INVOKE", ordinal = 1, shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/gui/widget/SliderWidget;drawTexture(IIIIII)V"))
	private void oldfaithful$knobDrawn(Minecraft minecraft, int mouseX, int mouseY, CallbackInfo ci) {
		GL11.glPopMatrix();
	}
}
