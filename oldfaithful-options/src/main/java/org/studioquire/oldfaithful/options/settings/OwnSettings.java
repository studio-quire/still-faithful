package org.studioquire.oldfaithful.options.settings;

import net.fabricmc.loader.api.FabricLoader;
import org.studioquire.oldfaithful.options.OptionsConfig;

/**
 * This mod's own switches on the screen, and Pure Faithful Display's when it's installed and working (it needs our
 * LWJGL component). Not the FOV (its slider is in Options), nor GUI Normal on a first start (there's no first start
 * left by the time anyone sees this screen); both stay in the file.
 */
public final class OwnSettings implements SettingsProvider {
	@Override
	public void register(SettingsRegistry r) {
		boolean display = FabricLoader.getInstance().isModLoaded("purefaithful-display") && DisplaySettings.working();
		r.changes("window", display ? "Window and Mouse" : "Game", 20);
		if (display) DisplaySettings.register(r);
		r.add("window", Setting.onOff("oldfaithful-options.properties", "title.quitGame", "Quit Game Button",
				"Quit Game on the title screen, beside Options.", true)
				.running(() -> String.valueOf(OptionsConfig.applied("title.quitGame"))));
	}
}
