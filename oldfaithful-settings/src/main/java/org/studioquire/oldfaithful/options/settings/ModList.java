package org.studioquire.oldfaithful.options.settings;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

/**
 * What other mods may ask of whatever chooses which mods the next launch loads, where a pack has such a choice (More Faithful's own jar): Faithful Core's
 * refusal screen matches a server's mods through it. A mod offering it lists its class as the {@value #ENTRYPOINT} entrypoint; without one there's
 * nothing to choose, and {@link #find} gives null.
 */
public interface ModList {
	String ENTRYPOINT = "oldfaithful-settings:mod-list";

	/** Whether it has any mods to choose from. */
	boolean any();

	/** The name of a mod it could load, by id; null if it has none by that id. */
	String name(String id);

	/**
	 * A server's set for the next launch, by the ids in its hello that must match: those on, with what they need, and every other mod that must match off.
	 * Mods that needn't match stay as they were, unless what one needs went.
	 */
	Matched match(Iterable<String> mustMatch);

	/** What {@link #match} did. */
	final class Matched {
		/** False if it couldn't be saved, and nothing changed. */
		public final boolean saved;
		/** The mods it turned on and off, by name. */
		public final List<String> on, off;
		/** Ids the server has that there's no jar for. */
		public final List<String> absent;

		public Matched(boolean saved, List<String> on, List<String> off, List<String> absent) {
			this.saved = saved;
			this.on = new ArrayList<>(on);
			this.off = new ArrayList<>(off);
			this.absent = new ArrayList<>(absent);
		}
	}

	/** The one there is, or null. */
	static ModList find() {
		try {
			List<ModList> found = FabricLoader.getInstance().getEntrypoints(ENTRYPOINT, ModList.class);
			return found.isEmpty() ? null : found.get(0);
		} catch (RuntimeException e) {
			System.out.println("[oldfaithful-settings] WARN Could not reach what chooses the mods: " + e);
			return null;
		}
	}
}
