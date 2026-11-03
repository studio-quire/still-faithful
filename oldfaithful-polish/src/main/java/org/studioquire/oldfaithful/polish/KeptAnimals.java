package org.studioquire.oldfaithful.polish;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.WolfEntity;

/**
 * #4: beta despawns every animal far enough from players (all but tamed wolves), penned ones too. Here an animal a
 * player has dealt with is kept for good, and wild ones despawn as before. Dealt with, as beta players deal with
 * animals (beta has no feeding, breeding or leads). Not hitting: the user's call (2026-11-02), hit animals needn't stay.
 * <ul>
 * <li>right-clicked to some effect: milked, sheared, ridden, or given an item that it took (a saddle, a dye);</li>
 * <li>riding something or ridden (an animal pushed into a minecart to carry it home, a saddled pig ridden);</li>
 * <li>hatched from an egg a player threw.</li>
 * </ul>
 * Cows, pigs, sheep and chickens; wolves keep beta's rule (tamed ones already stay; wild ones you've hit turn hostile).
 * Kept is saved with the animal as {@code PersistenceRequired}, the tag later versions use for the same thing, so such
 * a world carried forward keeps them too; beta ignores the tag (and drops it on its next save of the animal). Kept
 * animals still count towards beta's cap on animals spawning around a player (about 17 with one player), so a big farm
 * near you means fewer wild animals nearby.
 */
public final class KeptAnimals {
	public static final String TAG = "PersistenceRequired";

	public interface Keepable {
		boolean oldfaithful$kept();

		void oldfaithful$keep();
	}

	private KeptAnimals() {
	}

	public static boolean eligible(Entity e) {
		return e instanceof AnimalEntity && !(e instanceof WolfEntity) && e instanceof Keepable;
	}

	public static void keep(Entity e) {
		if (eligible(e) && !e.world.isRemote) ((Keepable) e).oldfaithful$keep();
	}

	public static boolean kept(Entity e) {
		return eligible(e) && ((Keepable) e).oldfaithful$kept();
	}
}
