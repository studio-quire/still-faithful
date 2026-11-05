package org.studioquire.oldfaithful.settings;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Reads the library's folder at each start, so a list that's gone stale is mended whether or not anyone opens the settings screen. */
public final class LibraryMend implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		new LibrarySelection(FabricLoader.getInstance().getGameDir());
	}
}
