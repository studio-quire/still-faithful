package org.studioquire.oldfaithful.options.mixin.fov;

import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.options.FovSlider;

/**
 * The FOV slider in the Options screen's empty place: beta lays five options out two to a row, so the third row has
 * Difficulty on the left and nothing on the right. If another mod has put something there, the slider stays out.
 */
@Mixin(OptionsScreen.class)
abstract class OptionsScreenMixin extends Screen {
	/** Not one of beta's ids on this screen (its options' ordinals, 100, 101 and 200). */
	@Unique
	private static final int ID = 7070;

	@Inject(method = "init", at = @At("TAIL"))
	private void oldfaithful$fovSlider(CallbackInfo ci) {
		int x = this.width / 2 + 5, y = this.height / 6 + 48;
		for (Object o : (List<?>) this.buttons) {
			ButtonWidget b = (ButtonWidget) o;
			if (b.x < x + 150 && x < b.x + 150 && b.y < y + 20 && y < b.y + 20) {
				System.out.println("[oldfaithful-options] The Options screen has something where the FOV slider goes; leaving it out");
				return;
			}
		}
		this.buttons.add(new FovSlider(this.minecraft, ID, x, y));
	}
}
