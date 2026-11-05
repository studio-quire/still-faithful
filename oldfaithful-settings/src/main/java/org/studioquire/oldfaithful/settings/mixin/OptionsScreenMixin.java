package org.studioquire.oldfaithful.settings.mixin;

import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.studioquire.oldfaithful.options.settings.SettingsScreen;

/**
 * "Old Faithful..." ("Mods..." on an edition without Old Faithful's options mod) in Options, opening the settings screen: as wide as Video Settings and Controls and on their pitch,
 * just above them, in the gap beta leaves below its options. Nothing of beta's moves. If another mod has put something
 * there, the button stays out (the switches are still in the config files).
 */
@Mixin(OptionsScreen.class)
abstract class OptionsScreenMixin extends Screen {
	/** Not one of beta's ids on this screen (its options' ordinals, 100, 101 and 200), nor the FOV slider's 7070. */
	@Unique
	private static final int ID = 7071;

	@Inject(method = "init", at = @At("TAIL"))
	private void oldfaithful$settingsButton(CallbackInfo ci) {
		int x = this.width / 2 - 100, y = this.height / 6 + 72 + 12;
		for (Object o : (List<?>) this.buttons) {
			ButtonWidget b = (ButtonWidget) o;
			// Beta's buttons here are 150 or 200 wide; 200 is the safe guess for anyone's.
			if (b.x < x + 200 && x < b.x + 200 && b.y < y + 20 && y < b.y + 20) {
				System.out.println("[oldfaithful-options] The Options screen has something where the settings button goes; leaving it out");
				return;
			}
		}
		this.buttons.add(new ButtonWidget(ID, x, y, SettingsScreen.defaultTitle() + "..."));
	}

	@Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
	private void oldfaithful$openSettings(ButtonWidget button, CallbackInfo ci) {
		if (button.id != ID || !button.active) return;
		this.minecraft.options.save();
		this.minecraft.setScreen(SettingsScreen.open(this));
		ci.cancel();
	}
}
