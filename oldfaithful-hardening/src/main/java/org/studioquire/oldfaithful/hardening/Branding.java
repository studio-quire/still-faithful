package org.studioquire.oldfaithful.hardening;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

/**
 * How a player can tell they're on Pure Faithful: one line under beta's own version on the title screen, in the same
 * grey ({@code client.TitleScreenMixin}). This mod carries it because every Pure Faithful setup has it.
 * <p>
 * An edition built on Pure Faithful names itself here instead: a mod whose fabric.mod.json has
 * {@code "custom": {"purefaithful-hardening:branding": "<name>"}} makes the line "<name> <that mod's version>"
 * (Old Faithful's fix mod does). If more than one does, the first by mod id wins.
 */
public final class Branding {
	public static final String KEY = "purefaithful-hardening:branding";
	public static final String TEXT = text();
	/** b1.7.3's colour for its own "Minecraft Beta 1.7.3" line. */
	public static final int COLOUR = 0x505050;

	private Branding() {
	}

	private static String text() {
		String pure = "";
		ModMetadata edition = null;
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ModMetadata meta = mod.getMetadata();
			if (meta.getId().equals("purefaithful-hardening")) pure = meta.getVersion().getFriendlyString();
			CustomValue name = meta.getCustomValue(KEY);
			if (name != null && name.getType() == CustomValue.CvType.STRING
					&& (edition == null || meta.getId().compareTo(edition.getId()) < 0)) {
				edition = meta;
			}
		}
		if (edition != null) {
			return edition.getCustomValue(KEY).getAsString() + " " + edition.getVersion().getFriendlyString();
		}
		return "Pure Faithful " + pure;
	}
}
