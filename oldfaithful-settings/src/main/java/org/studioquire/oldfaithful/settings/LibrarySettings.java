package org.studioquire.oldfaithful.settings;

import net.fabricmc.loader.api.FabricLoader;
import org.studioquire.oldfaithful.options.settings.Setting;
import org.studioquire.oldfaithful.options.settings.SettingsProvider;
import org.studioquire.oldfaithful.options.settings.SettingsRegistry;

/** A library pack's mods on the settings screen: each mod's page has its on and off, for the next launch. Nothing shows without the folder. */
public final class LibrarySettings implements SettingsProvider {
	@Override
	public void register(SettingsRegistry settings) {
		LibrarySelection library = new LibrarySelection(FabricLoader.getInstance().getGameDir());
		if (library.entries().isEmpty()) return;
		for (LibrarySelection.Entry e : library.entries()) {
			settings.mod(e.id, e.name);
			settings.modSwitch(e.id, Setting.stored("library." + e.id, "Enabled", e.description, false, new Setting.Store() {
				private String also;

				@Override
				public String read() {
					return String.valueOf(library.isOn(e));
				}

				@Override
				public boolean write(String value) {
					LibrarySelection.Change change = library.set(e, Boolean.parseBoolean(value));
					also = change.also();
					return change.saved;
				}

				@Override
				public String said() {
					return also;
				}
			}).running(() -> String.valueOf(FabricLoader.getInstance().isModLoaded(e.id))));
		}
	}
}
