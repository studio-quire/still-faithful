package org.studioquire.oldfaithful.speed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** ChunkMap answers as HashMap does, through random puts, gets and removes on chunk-shaped keys. */
class ChunkMapTest {
	private static int key(int x, int z) {
		return (x < 0 ? Integer.MIN_VALUE : 0) | (x & 32767) << 16 | (z < 0 ? 32768 : 0) | z & 32767; // ChunkPos.hashCode
	}

	@Test
	void behavesAsHashMap() {
		Random r = new Random(404);
		Map<Integer, Integer> ref = new HashMap<>();
		ChunkMap<Integer, Integer> map = new ChunkMap<>();
		for (int step = 0; step < 400_000; step++) {
			Integer k = key(r.nextInt(80) - 40, r.nextInt(80) - 40);
			switch (r.nextInt(5)) {
				case 0: case 1: assertEquals(ref.put(k, step), map.put(k, step)); break;
				case 2: assertEquals(ref.remove(k), map.remove(k)); break;
				case 3: assertEquals(ref.containsKey(k), map.containsKey(k)); break;
				default: assertEquals(ref.get(k), map.get(k)); break;
			}
			assertEquals(ref.size(), map.size());
			if (step % 50_000 == 0) assertEquals(ref, map);
		}
		assertEquals(ref, map);
		for (Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Integer> e = it.next();
			if ((e.getValue() & 1) == 0) it.remove();
		}
		ref.values().removeIf(v -> (v & 1) == 0);
		assertEquals(ref, map);
		map.put(null, -1);
		ref.put(null, -1);
		assertEquals(ref, map);
		assertEquals(-1, (int) map.get(null));
	}
}
