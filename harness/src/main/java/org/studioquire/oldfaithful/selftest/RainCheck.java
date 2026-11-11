package org.studioquire.oldfaithful.selftest;

import net.minecraft.client.Minecraft;

/**
 * Whether a client has the server's rain, for a server-side mod's test: {@code -Doldfaithful.selftest.expectRain=true} waits for it, as a server says it's raining in a packet of its own
 * (70) after the login, and fails if it never comes in ten seconds of ticks or the game isn't being played by then.
 */
public final class RainCheck implements WorldCheck {
	private static final boolean EXPECTED = Boolean.getBoolean("oldfaithful.selftest.expectRain");
	private static final int LIMIT = 200;

	private SelfTest results;
	private int ticks;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
	}

	@Override
	public boolean tick(Minecraft mc) {
		if (!EXPECTED) return true;
		if (!mc.world.isRaining() && ++ticks < LIMIT) return false;
		results.check("the client has the server's rain, and plays", mc.world.isRaining() && mc.player != null && mc.currentScreen == null);
		return true;
	}
}
