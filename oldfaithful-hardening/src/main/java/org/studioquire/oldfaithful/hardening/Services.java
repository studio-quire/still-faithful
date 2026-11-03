package org.studioquire.oldfaithful.hardening;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

import org.studioquire.oldfaithful.hardening.skins.Skins;
import org.studioquire.oldfaithful.hardening.util.Https;

/** Shared state for the mixins, which can't hold it themselves. */
public final class Services {
	public static final Skins SKINS = new Skins(Https.REAL);

	private Services() {
	}

	public static Path cacheDir() {
		return FabricLoader.getInstance().getGameDir().resolve("cache").resolve("purefaithful-hardening");
	}
}
