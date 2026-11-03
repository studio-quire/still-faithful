package org.studioquire.oldfaithful.lighting;

import net.minecraft.world.World;

/**
 * {@code -Doldfaithful.lighting.stats=true}: every ten seconds, how many chunks were checked and how long that took,
 * how many cells disagreed with the rule, and the largest the lighting queue grew, per world. For measuring; off by
 * default.
 */
public final class Stats {
	public static final boolean ENABLED = Boolean.getBoolean("oldfaithful.lighting.stats");
	private static long since = System.nanoTime();
	private static long chunks, checkNs, wrongCells, maxQueue, passes, updates;

	private Stats() {
	}

	static void checked(int n, long ns) {
		chunks += n;
		checkNs += ns;
	}

	public static void update() {
		updates++;
	}

	static void wrong(int cells) {
		wrongCells += cells;
	}

	public static void pass(World world, int queue) {
		passes++;
		maxQueue = Math.max(maxQueue, queue);
		long now = System.nanoTime();
		if (now - since < 10_000_000_000L) return;
		log(String.format("%d chunks checked in %.1f ms (%.3f ms each), %d cells queued as wrong, %d updates run, queue at most %d, %d passes",
				chunks, checkNs / 1e6, chunks == 0 ? 0 : checkNs / 1e6 / chunks, wrongCells, updates, maxQueue, passes));
		since = now;
		chunks = checkNs = wrongCells = maxQueue = passes = updates = 0;
	}

	public static void log(String message) {
		System.out.println("[oldfaithful-lighting] " + message);
	}
}
