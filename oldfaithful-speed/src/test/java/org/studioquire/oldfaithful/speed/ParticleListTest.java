package org.studioquire.oldfaithful.speed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** ParticleList against ArrayList: the same calls give the same answers and leave the same elements in the same order. */
class ParticleListTest {
	@Test
	void behavesAsArrayList() {
		Random r = new Random(1733);
		List<Integer> ref = new ArrayList<>();
		List<Integer> list = new ParticleList<>();
		int largest = 0;
		for (int step = 0; step < 300_000; step++) {
			int size = ref.size();
			largest = Math.max(largest, size);
			// Phases of growing and shrinking, so sizes from empty to over a thousand all come up.
			int adds = (step / 20_000) % 2 == 0 ? 550 : 330;
			int op = r.nextInt(1000);
			if (size == 0 || op < adds) {
				if (op % 5 == 0) {
					int i = r.nextInt(size + 1);
					ref.add(i, step);
					list.add(i, step);
				} else {
					assertEquals(ref.add(step), list.add(step));
				}
			} else if (op < 750) {
				// Where removals come in the game: the front (the cap), walking forward (the tick), and anywhere.
				int i = op % 4 == 0 ? 0 : op % 4 == 1 ? size - 1 : op % 4 == 2 ? (step * 7) % size : r.nextInt(size);
				assertEquals(ref.remove(i), list.remove(i));
			} else if (op < 850) {
				int i = r.nextInt(size);
				assertEquals(ref.set(i, -step), list.set(i, -step));
			} else if (op < 950) {
				int i = r.nextInt(size);
				assertEquals(ref.get(i), list.get(i));
				assertEquals(ref.indexOf(ref.get(i)), list.indexOf(ref.get(i)));
			} else if (op < 952) {
				// An iterator dropping some as it goes.
				int mod = 2 + r.nextInt(60);
				for (Iterator<Integer> a = ref.iterator(), b = list.iterator(); a.hasNext(); ) {
					Integer v = a.next();
					assertEquals(v, b.next());
					if (Math.floorMod(v ^ step, mod) == 0) {
						a.remove();
						b.remove();
					}
				}
			} else if (op < 970) {
				int from = r.nextInt(size + 1), to = from + r.nextInt(Math.min(20, size - from) + 1);
				ref.subList(from, to).clear();
				list.subList(from, to).clear();
			} else if (op < 980) {
				ListIterator<Integer> a = ref.listIterator(size / 2), b = list.listIterator(size / 2);
				a.add(step);
				b.add(step);
			} else if (op == 999 && r.nextInt(4) == 0) {
				ref.clear();
				list.clear();
			}
			assertEquals(ref.size(), list.size());
			if (size < 200 || step % 97 == 0) {
				assertEquals(ref, list);
				assertNothingHeldOutside(list);
			}
		}
		assertEquals(ref, list);
		assertTrue(largest > 1000, "largest size " + largest);
	}

	/** Slots outside the elements are null, so nothing removed stays reachable through the list. */
	static void assertNothingHeldOutside(List<?> list) {
		try {
			Object[] a = (Object[]) field("a").get(list);
			int head = field("head").getInt(list), front = field("front").getInt(list), gap = field("gap").getInt(list),
					back = field("back").getInt(list);
			for (int i = 0; i < a.length; i++) {
				boolean element = i >= head && i < head + front || i >= head + front + gap && i < head + front + gap + back;
				if (!element) assertNull(a[i], "slot " + i + " outside the elements still holds something");
			}
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static java.lang.reflect.Field field(String name) throws NoSuchFieldException {
		java.lang.reflect.Field f = ParticleList.class.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}

	@Test
	void outOfRangeThrowsAsArrayListDoes() {
		List<Integer> list = new ParticleList<>();
		list.add(1);
		list.add(2);
		assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
		assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> list.remove(2));
		assertThrows(IndexOutOfBoundsException.class, () -> list.set(-1, 0));
		assertThrows(IndexOutOfBoundsException.class, () -> list.add(3, 0));
		assertEquals(java.util.Arrays.asList(1, 2), list);
	}

	/** A particle as the manager sees it: a tick that may kill it and may add others, as lava adds smoke. */
	static final class P {
		final int id, layer;
		final Random own;
		int age;
		final int life;
		boolean dead;

		P(int id, int layer) {
			this.id = id;
			this.layer = layer;
			this.own = new Random(id);
			this.life = 1 + own.nextInt(40);
		}
	}

	/** The steps of b1.7.3's ParticleManager.addParticle and removeDeadParticles, over whatever lists it's given. */
	static final class Manager {
		final List<P>[] particles;
		int nextId;
		/** Times the cap dropped a layer's first particle while the tick was going through the layers. */
		int midPassDrops;
		boolean inPass;

		Manager(List<P>[] layers) {
			particles = layers;
		}

		/** At the cap the layer's oldest goes first; then the new one joins the end. */
		void addParticle(P p) {
			List<P> layer = particles[p.layer];
			if (layer.size() >= 4000) {
				if (inPass) midPassDrops++;
				layer.remove(0);
			}
			layer.add(p);
		}

		/** Each layer in turn, by index, ticking each particle and taking out a dead one where it stands. */
		void removeDeadParticles() {
			inPass = true;
			for (List<P> layer : particles) {
				for (int i = 0; i < layer.size(); i++) {
					P p = layer.get(i);
					tick(p);
					if (p.dead) layer.remove(i--);
				}
			}
			inPass = false;
		}

		void tick(P p) {
			if (++p.age >= p.life) p.dead = true;
			// Some spawn others mid-pass, into either layer, which at the cap drops the first of that layer and shifts
			// the loop's place: beta then skips a particle's tick, or removes the one after a dead one. Kept exactly.
			if (p.own.nextInt(8) == 0) addParticle(new P(nextId++, p.own.nextInt(2)));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void theParticleLoopAsBeta() {
		Manager beta = new Manager(new List[] {new ArrayList<P>(), new ArrayList<P>()});
		Manager ours = new Manager(new List[] {new ParticleList<P>(), new ParticleList<P>()});
		Random burst = new Random(7);
		for (int tick = 0; tick < 600; tick++) {
			// Blasts now and then, some past the cap at once; a trickle otherwise.
			int n = tick % 50 == 0 ? 3000 + burst.nextInt(4000) : burst.nextInt(30);
			for (int i = 0; i < n; i++) {
				int layer = burst.nextInt(4) == 0 ? 1 : 0;
				beta.addParticle(new P(beta.nextId++, layer));
				ours.addParticle(new P(ours.nextId++, layer));
			}
			beta.removeDeadParticles();
			ours.removeDeadParticles();
			for (int layer = 0; layer < 2; layer++) {
				List<P> a = beta.particles[layer], b = ours.particles[layer];
				assertEquals(a.size(), b.size(), "layer " + layer + " at tick " + tick);
				for (int i = 0; i < a.size(); i++) {
					assertEquals(a.get(i).id, b.get(i).id, "layer " + layer + " at tick " + tick);
					assertEquals(a.get(i).age, b.get(i).age, "layer " + layer + " at tick " + tick);
				}
				assertNothingHeldOutside(b);
			}
		}
		assertEquals(beta.midPassDrops, ours.midPassDrops);
		assertTrue(ours.midPassDrops > 1000, "the cap was met mid-pass " + ours.midPassDrops + " times");
	}
}
