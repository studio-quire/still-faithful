package org.studioquire.oldfaithful.lighting;

import org.studioquire.oldfaithful.options.settings.Setting;
import org.studioquire.oldfaithful.options.settings.SettingsProvider;
import org.studioquire.oldfaithful.options.settings.SettingsRegistry;

/**
 * The lighting's one switch on Old Faithful's settings screen (oldfaithful-options), beside the redstone fixes. It
 * takes effect at once: the check reads it per chunk, and either value leaves the light it finds consistent (lit
 * lava is a source under both), so changing it mid-game only changes how land loading from then on is lit. Only
 * loaded by the options mod, on the client.
 */
public final class LightingSettings implements SettingsProvider {
	@Override
	public void register(SettingsRegistry r) {
		r.fixes("redstone", "Redstone and Lighting", 30);
		r.add("redstone", Setting.fix(LightingConfig.FILE, LightingConfig.LAVA_KEY, "Lava Light",
						"Lava that came with new land gave no light until disturbed. Changes land as it loads.")
				.serverDecides()
				.applyNow(v -> LightingConfig.setLightGeneratedLava(Boolean.parseBoolean(v))));
	}
}
