package org.studioquire.oldfaithful.options.settings;

/**
 * A mod's switches for Old Faithful's settings screen. Listed in the mod's fabric.mod.json under the entrypoint
 * {@value SettingsRegistry#ENTRYPOINT}; the options mod calls it the first time the screen opens, so a mod without the
 * options mod beside it (or on a server) never loads the class.
 */
public interface SettingsProvider {
	void register(SettingsRegistry settings);
}
