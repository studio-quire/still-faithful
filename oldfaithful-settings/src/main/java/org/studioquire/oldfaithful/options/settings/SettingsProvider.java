package org.studioquire.oldfaithful.options.settings;

/**
 * A mod's switches for the settings screen. Listed in the mod's fabric.mod.json under the entrypoint
 * {@value SettingsRegistry#ENTRYPOINT}; it's called the first time the screen opens, so a mod without Faithful Settings
 * beside it (or on a server) never loads the class. {@link SettingsRegistry#add(Setting)} puts a switch on the mod's own page.
 */
public interface SettingsProvider {
	void register(SettingsRegistry settings);
}
