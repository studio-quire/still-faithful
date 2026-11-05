package org.studioquire.oldfaithful.options.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;

/** The Mods menu: a wide button for each mod's page, five to a page, with whether it's on said beside it. */
public class SettingsModsScreen extends SettingsScreen {
	public SettingsModsScreen(Screen parent) {
		super(parent);
		this.title = "Mods";
	}

	@Override
	List<Entry> entries() {
		SettingsRegistry registry = SettingsRegistry.get();
		List<Entry> mods = new ArrayList<>();
		for (SettingsRegistry.Group g : registry.mods()) {
			String on = g.modSwitch == null ? "" : g.modSwitch.word(registry.value(g.modSwitch));
			String state = on + (registry.waitingForRestart(g) ? WAITING : "");
			mods.add(new Entry(g.title + "...", g.modSwitch == null ? null : g.modSwitch.hint, state.isEmpty() ? null : state,
					parent -> new SettingsGroupScreen(parent, null, Collections.singletonList(g))));
		}
		return mods;
	}

	@Override
	String waitingLine() {
		SettingsRegistry registry = SettingsRegistry.get();
		for (SettingsRegistry.Group g : registry.mods()) {
			if (registry.waitingForRestart(g)) return WAITING + " Takes effect after a restart";
		}
		return null;
	}
}
