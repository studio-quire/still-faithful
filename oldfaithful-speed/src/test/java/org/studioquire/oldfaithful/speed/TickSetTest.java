package org.studioquire.oldfaithful.speed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import net.minecraft.world.BlockEvent;
import org.junit.jupiter.api.Test;

/** TickSet answers as HashSet does, through random adds, contains and removes of the game's own BlockEvents. */
class TickSetTest {
	/** Spread over 1,200 blocks in x and z, so many ticks share BlockEvent.hashCode (x repeats every 128). */
	private static BlockEvent event(Random r) {
		BlockEvent e = new BlockEvent(r.nextInt(1200) - 600, 60 + r.nextInt(4), r.nextInt(1200) - 600, r.nextBoolean() ? 12 : 13);
		e.ticks = r.nextInt(100); // not part of equality: a copy with other ticks is the same tick
		return e;
	}

	@Test
	void behavesAsHashSet() {
		Random r = new Random(404);
		Set<Object> ref = new HashSet<>();
		TickSet set = new TickSet();
		// Few enough positions that adds, contains and removes keep meeting ticks already there.
		BlockEvent[] pool = new BlockEvent[20_000];
		for (int i = 0; i < pool.length; i++) pool[i] = event(r);
		for (int step = 0; step < 400_000; step++) {
			BlockEvent p = pool[r.nextInt(pool.length)];
			BlockEvent e = new BlockEvent(p.x, p.y, p.z, p.blockId);
			switch (r.nextInt(5)) {
				case 0: case 1: assertEquals(ref.add(e), set.add(e)); break;
				case 2: assertEquals(ref.remove(e), set.remove(e)); break;
				default: assertEquals(ref.contains(e), set.contains(e)); break;
			}
			assertEquals(ref.size(), set.size());
			if (step % 50_000 == 0) assertEquals(ref, set);
		}
		assertEquals(ref, set);

		assertEquals(ref.add(null), set.add(null));
		assertEquals(ref.contains(null), set.contains(null));
		assertEquals(ref.contains("not a tick"), set.contains("not a tick"));
		assertEquals(ref, set);

		for (Iterator<Object> it = set.iterator(); it.hasNext(); ) {
			Object o = it.next();
			if (o == null || ((BlockEvent) o).x % 3 == 0) it.remove();
		}
		ref.removeIf(o -> o == null || ((BlockEvent) o).x % 3 == 0);
		assertEquals(ref, set);

		set.clear();
		assertEquals(0, set.size());
		assertEquals(false, set.contains(pool[0]));
	}
}
