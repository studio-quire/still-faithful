package org.studioquire.oldfaithful.selftest;

import net.minecraft.client.Minecraft;

/**
 * A check another test-only mod runs in the start-up test's world, listed under the {@link #ENTRYPOINT} entrypoint:
 * started once the world runs (after the Fixed edition's fixes, where they're loaded), then ticked until it says it's
 * done; the options checks follow. Results go through the {@link SelfTest} it's given.
 */
public interface WorldCheck {
	String ENTRYPOINT = "oldfaithful-selftest:world";

	void start(Minecraft mc, SelfTest results);

	/** Called every game tick; true when done. */
	boolean tick(Minecraft mc);
}
