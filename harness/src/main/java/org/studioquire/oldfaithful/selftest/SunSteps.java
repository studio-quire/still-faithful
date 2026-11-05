package org.studioquire.oldfaithful.selftest;

import net.minecraft.client.Minecraft;

/**
 * A client with nothing of the library on a server with a longer day (`:harness:libraryRulesTest`): what its sun does, measured. It runs beta's pace and the server keeps pulling it back, so this
 * reports how far, and that the game goes on. {@code -Doldfaithful.selftest.sunSteps=<ticks>} turns it on.
 */
public final class SunSteps implements WorldCheck {
	private static final int TICKS = Integer.getInteger("oldfaithful.selftest.sunSteps", 0);

	private SelfTest results;
	private int ticks, steps, changes, darkness, firstDarkness;
	private float last = -1.0F, most;

	@Override
	public void start(Minecraft mc, SelfTest results) {
		this.results = results;
		firstDarkness = darkness = mc.world == null ? 0 : mc.world.ambientDarkness;
	}

	@Override
	public boolean tick(Minecraft mc) {
		if (TICKS == 0) return true;
		float sun = mc.world.getTime(1.0F);
		if (last >= 0.0F && sun < last) {
			steps++;
			most = Math.max(most, last - sun);
		}
		last = sun;
		if (mc.world.ambientDarkness != darkness) {
			darkness = mc.world.ambientDarkness;
			changes++;
		}
		if (++ticks < TICKS) return false;
		results.pass("sun steps: back " + steps + " times in " + ticks + " ticks, at most " + most * 360.0F + " degrees (" + most * 24000.0F + " of a day's 24000), darkness went from " + firstDarkness + " to " + darkness + " in "
				+ changes + " changes, raining " + mc.world.isRaining());
		results.check("a plain client on a server with a longer day still plays", mc.world != null && mc.player != null && mc.currentScreen == null);
		results.check("its sun goes back no more than a few clock ticks, and its darkness doesn't cross a step twice", most * 24000.0F < 5.0F && changes <= Math.abs(darkness - firstDarkness));
		return true;
	}
}
