package org.studioquire.oldfaithful.saves;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.WorldProperties;

/**
 * A dedicated server always makes the Nether, which reads its own copy of level.dat's data at start-up and, in every
 * full save (save-all, stopping), writes level.dat after the overworld. With {@code allow-nether=false} it's never
 * ticked, so its copy stays as it was loaded, and that last write puts back the time, weather and (on a new world) the
 * Nether's own spawn: every restart loses the session's time (docs/bugs.md #52). The copy of a Nether that won't tick is
 * kept here and never written; the overworld's, written just before it and by every autosave, stands. With the Nether
 * on, nothing changes.
 */
public final class UntickedNether {
	/** By identity (WorldProperties has no equals); weak so a world's data isn't held past its world. */
	private static final Set<WorldProperties> stale = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private UntickedNether() {
	}

	public static void markStale(WorldProperties properties) {
		stale.add(properties);
		Log.info("allow-nether=false: level.dat is saved from the overworld alone (the unticked Nether's copy would reset its time and spawn)");
	}

	public static boolean isStale(WorldProperties properties) {
		return stale.contains(properties);
	}
}
