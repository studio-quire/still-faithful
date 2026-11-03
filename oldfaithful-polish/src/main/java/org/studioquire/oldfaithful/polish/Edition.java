package org.studioquire.oldfaithful.polish;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Still Faithful's name where the editions show theirs: the line under beta's version on the title screen (Pure
 * Faithful's hardening mod draws it, naming Old Faithful when its fix mod is there) and Old Faithful's settings screen
 * (its options mod). Both are those mods' own code, released with their editions, so this mod renames rather than
 * having them changed.
 */
public final class Edition {
	public static final String NAME = "Still Faithful";
	public static final String MOD_ID = "stillfaithful-polish";

	private Edition() {
	}

	public static String titleLine() {
		return NAME + " " + FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
	}
}
