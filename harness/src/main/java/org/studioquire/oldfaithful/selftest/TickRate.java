package org.studioquire.oldfaithful.selftest;

/**
 * Faster game time for tests that wait on it ({@code -Ptest.speed}, docs/building.md): how long the client's ticks
 * take now, 50 ms as in the game unless a faster single player world or server set it (MinecraftTickRateMixin,
 * ClientNetworkHandlerTickRateMixin). What else in the game goes by real time and has to keep pace reads it here.
 */
public final class TickRate {
	/** {@code -Doldfaithful.test.tickMs}: what single player runs at. */
	public static final int SINGLE_PLAYER_MS = Integer.getInteger("oldfaithful.test.tickMs", 50);
	public static volatile int ms = 50;

	private TickRate() {
	}
}
