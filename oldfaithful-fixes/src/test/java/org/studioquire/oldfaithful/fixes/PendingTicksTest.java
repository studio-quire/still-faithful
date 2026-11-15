package org.studioquire.oldfaithful.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.BlockEvent;
import org.junit.jupiter.api.Test;

/**
 * A chunk's ticks as {@link PendingTicks#collect} gives them, against reading the whole queue for that chunk (what the
 * mod did before it indexed the queue), through everything that happens to a world's queue, with the game's own
 * BlockEvents: ticks queued and run as beta does it, parked and put back, chunks saved and unloaded, the clock moved,
 * the set swapped under it, ticks taken off through the set in every way it offers. And the sweep of parked ticks
 * against beta's own region check asked for each tick.
 */
class PendingTicksTest {
	private static final int CHUNKS = 5;

	private static final class World implements PendingTicks.Queue {
		Set<BlockEvent> pending = new HashSet<>();
		TreeSet<BlockEvent> ordered = new TreeSet<>();
		final Map<Long, List<BlockEvent>> parked = new HashMap<>();
		long now = 1000;

		@Override
		public Set<BlockEvent> oldfaithful$pending() {
			return pending;
		}

		@Override
		public void oldfaithful$setPending(Set<BlockEvent> pending) {
			this.pending = pending;
		}

		@Override
		public TreeSet<BlockEvent> oldfaithful$ordered() {
			return ordered;
		}

		@Override
		public Map<Long, List<BlockEvent>> oldfaithful$parked() {
			return parked;
		}

		/** As World.scheduleBlockUpdate. */
		void schedule(BlockEvent e, int delay) {
			e.get(now + delay);
			if (!pending.contains(e)) {
				pending.add(e);
				ordered.add(e);
			}
		}

		/** As World.processScheduledTicks, parking some as the mod does for a tick whose surroundings aren't loaded. */
		void run(int most, Random r) {
			for (int i = 0; i < most && !ordered.isEmpty(); i++) {
				BlockEvent e = ordered.first();
				if (e.ticks > now) break;
				ordered.remove(e);
				pending.remove(e);
				if (r.nextInt(4) == 0) parked.computeIfAbsent(PendingTicks.key(e.x >> 4, e.z >> 4), k -> new ArrayList<>()).add(e);
			}
		}

		/** As PendingTicks.sweep, for some of the parked. */
		void unpark(Random r) {
			for (Iterator<List<BlockEvent>> lists = parked.values().iterator(); lists.hasNext();) {
				List<BlockEvent> list = lists.next();
				for (Iterator<BlockEvent> it = list.iterator(); it.hasNext();) {
					BlockEvent e = it.next();
					if (r.nextBoolean()) continue;
					it.remove();
					e.get(now);
					if (pending.add(e)) ordered.add(e);
				}
				if (list.isEmpty()) lists.remove();
			}
		}

		/** The chunk's ticks by the whole queue read for it, then its parked ones: x, y, z, id and the delay left. */
		List<List<Integer>> wholeQueueRead(int chunkX, int chunkZ) {
			List<List<Integer>> out = new ArrayList<>();
			for (BlockEvent e : ordered) {
				if (e.x >> 4 == chunkX && e.z >> 4 == chunkZ) out.add(tick(e));
			}
			List<BlockEvent> waiting = parked.get(PendingTicks.key(chunkX, chunkZ));
			if (waiting != null) {
				for (BlockEvent e : waiting) out.add(tick(e));
			}
			return out;
		}

		private List<Integer> tick(BlockEvent e) {
			return Arrays.asList(e.x, e.y, e.z, e.blockId, (int) (e.ticks - now));
		}

		int held() {
			int n = ordered.size();
			for (List<BlockEvent> list : parked.values()) n += list.size();
			return n;
		}
	}

	private static List<List<Integer>> read(NbtList ticks) {
		List<List<Integer>> out = new ArrayList<>();
		for (int i = 0; i < ticks.size(); i++) {
			NbtCompound t = (NbtCompound) ticks.get(i);
			out.add(Arrays.asList(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("i"), t.getInt("t")));
		}
		return out;
	}

	/** Few enough places that ticks meet ones already queued, around 0, 0 so chunk positions are negative too. */
	private static BlockEvent event(Random r) {
		return new BlockEvent(r.nextInt(CHUNKS * 16) - 40, 60 + r.nextInt(3), r.nextInt(CHUNKS * 16) - 40, 8 + r.nextInt(3));
	}

	private static int chunk(Random r) {
		return (r.nextInt(CHUNKS * 16) - 40) >> 4;
	}

	@Test
	void collectsWhatAWholeQueueReadWould() {
		Random r = new Random(42);
		World world = new World();
		int saved = 0;
		int unloaded = 0;
		for (int step = 0; step < 60_000; step++) {
			switch (r.nextInt(12)) {
				case 0: case 1: case 2: case 3:
					for (int i = r.nextInt(30); i > 0; i--) world.schedule(event(r), r.nextInt(40));
					break;
				case 4: case 5:
					world.now += r.nextInt(3);
					world.run(r.nextInt(40), r);
					break;
				case 6:
					world.unpark(r);
					break;
				case 7: {
					// A chunk unloads: its ticks leave the world with it.
					int x = chunk(r);
					int z = chunk(r);
					List<List<Integer>> want = world.wholeQueueRead(x, z);
					int held = world.held();
					assertEquals(want, read(PendingTicks.collect(world, world.now, x, z, true)));
					assertEquals(held - want.size(), world.held());
					assertEquals(world.ordered.size(), world.pending.size());
					assertEquals(new ArrayList<>(), world.wholeQueueRead(x, z));
					for (BlockEvent e : world.pending) assertFalse(e.x >> 4 == x && e.z >> 4 == z);
					unloaded += want.size();
					break;
				}
				case 8: {
					// The clock set (World.synchronizeTimeAndUpdates): every tick moves with it.
					int by = r.nextInt(2000) - 1000;
					for (BlockEvent e : world.pending) e.ticks += by;
					world.now += by;
					break;
				}
				case 9:
					// Another mod swaps the world's set for its own, as the speed mod does.
					if (r.nextInt(50) == 0) world.pending = new HashSet<>(world.pending);
					break;
				case 10:
					// Ticks taken off through the set's iterator.
					for (Iterator<BlockEvent> it = world.pending.iterator(); it.hasNext();) {
						BlockEvent e = it.next();
						if (r.nextInt(20) != 0) continue;
						it.remove();
						world.ordered.remove(e);
					}
					break;
				case 11:
					switch (r.nextInt(4)) {
						case 0: {
							// Ticks taken off the set by a tick made anew, equal to the one held, and off the queue by
							// the one held.
							List<BlockEvent> held = new ArrayList<>(world.ordered);
							for (int i = r.nextInt(10); i > 0 && !held.isEmpty(); i--) {
								BlockEvent e = held.remove(r.nextInt(held.size()));
								assertTrue(world.pending.remove(new BlockEvent(e.x, e.y, e.z, e.blockId)));
								world.ordered.remove(e);
							}
							break;
						}
						case 1: {
							// By removeIf and removeAll.
							Set<BlockEvent> gone = new HashSet<>();
							int chunkX = chunk(r);
							world.pending.removeIf(e -> e.x >> 4 == chunkX && r.nextBoolean() && gone.add(e));
							List<BlockEvent> some = new ArrayList<>();
							for (BlockEvent e : world.pending) {
								if (r.nextInt(30) == 0) some.add(e);
							}
							world.pending.removeAll(some);
							gone.addAll(some);
							world.ordered.removeAll(gone);
							break;
						}
						case 2:
							if (r.nextInt(20) == 0) {
								world.pending.clear();
								world.ordered.clear();
							}
							break;
						default:
							break;
					}
					break;
				default:
					break;
			}
			// A save, of one chunk or several with the queue standing still.
			for (int i = r.nextInt(4); i > 0; i--) {
				int x = chunk(r);
				int z = chunk(r);
				List<List<Integer>> want = world.wholeQueueRead(x, z);
				int held = world.held();
				assertEquals(want, read(PendingTicks.collect(world, world.now, x, z, false)));
				assertEquals(held, world.held());
				saved += want.size();
			}
		}
		// The run did meet what it's for.
		assertTrue(saved > 100_000 && unloaded > 10_000, saved + " saved, " + unloaded + " unloaded");
		assertTrue(world.pending instanceof PendingTicks.Indexed);
	}

	/** The point of the index: made from the queue once, then kept up to date however the queue changes. */
	@Test
	void indexesOnce() {
		Random r = new Random(7);
		World world = new World();
		for (int i = 0; i < 2000; i++) world.schedule(event(r), r.nextInt(40));
		PendingTicks.collect(world, world.now, 0, 0, false);
		PendingTicks.Indexed indexed = (PendingTicks.Indexed) world.pending;
		assertEquals(1, indexed.builds);
		for (int round = 0; round < 50; round++) {
			for (int i = r.nextInt(40); i > 0; i--) world.schedule(event(r), r.nextInt(40));
			world.now += 3;
			world.run(30, r);
			world.unpark(r);
			int x = chunk(r);
			int z = chunk(r);
			assertEquals(world.wholeQueueRead(x, z), read(PendingTicks.collect(world, world.now, x, z, r.nextBoolean())));
		}
		assertEquals(1, indexed.builds);

		// Another queue in the world's field, though it holds the same ticks.
		world.ordered = new TreeSet<>(world.ordered);
		PendingTicks.collect(world, world.now, 0, 0, false);
		assertEquals(2, indexed.builds);

		// The queue changed behind the set's back (beta stops at its next tick): a tick gone from it isn't written.
		BlockEvent first = world.ordered.first();
		world.ordered.remove(first);
		assertEquals(world.wholeQueueRead(first.x >> 4, first.z >> 4), read(PendingTicks.collect(world, world.now, first.x >> 4, first.z >> 4, false)));
		assertEquals(3, indexed.builds);
	}

	/** A chunk's array of ticks against a TreeSet of the same, through growing, draining from either end and the middle. */
	@Test
	void chunkTicksKeepTheQueuesOrder() {
		Random r = new Random(11);
		PendingTicks.ChunkTicks ticks = new PendingTicks.ChunkTicks();
		TreeSet<BlockEvent> want = new TreeSet<>();
		// As the world's set has it, one tick to a block.
		Set<BlockEvent> blocks = new HashSet<>();
		long now = 0;
		for (int step = 0; step < 200_000; step++) {
			// Phases that grow it to thousands and drain it to nothing, so it widens and narrows both ways.
			int phase = step / 4_000 % 4;
			int op = r.nextInt(10);
			if (phase == 0 || phase == 2 && op < 5) {
				BlockEvent e = new BlockEvent(r.nextInt(16), r.nextInt(128), r.nextInt(16), 8);
				// Mostly due later than what's there (as new ticks are), sometimes sooner.
				e.get(op < 7 ? now + 1 + r.nextInt(40) : now - r.nextInt(40));
				if (!blocks.add(e)) continue;
				ticks.add(e);
				want.add(e);
			} else if (!want.isEmpty()) {
				BlockEvent e = op < 6 ? want.first() : op < 8 ? want.last() : pick(want, r);
				// Sometimes by a tick equal to it but made anew, as the set may be asked.
				assertTrue(ticks.remove(op == 9 ? new BlockEvent(e.x, e.y, e.z, e.blockId) : e));
				want.remove(e);
				blocks.remove(e);
			}
			now += r.nextInt(2);
			assertEquals(want.size(), ticks.size);
			if (step % 997 == 0) assertEquals(new ArrayList<>(want), Arrays.asList(ticks.toArray()));
		}
		assertFalse(ticks.remove(new BlockEvent(99, 0, 99, 8)));
	}

	private static BlockEvent pick(TreeSet<BlockEvent> set, Random r) {
		int i = r.nextInt(set.size());
		for (BlockEvent e : set) {
			if (i-- == 0) return e;
		}
		throw new AssertionError();
	}

	/**
	 * Beta's rule for whether a box's land is loaded (World.isRegionLoaded): never for a box wholly below 0 or from 128
	 * up, otherwise when every chunk column it spans is loaded. Over a set of loaded chunks, counting what it's asked.
	 */
	private static final class Loaded implements PendingTicks.Region {
		final Set<Long> chunks = new HashSet<>();
		int asked;

		@Override
		public boolean isLoaded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			asked++;
			if (maxY < 0 || minY >= 128) return false;
			for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
				for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
					if (!chunks.contains(PendingTicks.key(cx, cz))) return false;
				}
			}
			return true;
		}
	}

	/**
	 * The sweep against asking the region check for every parked tick, as it did: the same ticks back on the queue, in
	 * the same order, the same left parked. Ticks in every quarter of their chunk, at heights in and out of the world,
	 * in chunks at the ends of int (where a tick's span is no longer two chunks), some parked twice over.
	 */
	@Test
	void sweepAsksLessForTheSameAnswers() {
		Random r = new Random(5);
		int[] far = {Integer.MAX_VALUE >> 4, Integer.MIN_VALUE >> 4};
		// Heights either side of where the region check's own height test turns.
		int[] edges = {-9, -8, -1, 0, 7, 8, 119, 120, 127, 128, 135, 136};
		int askedBefore = 0;
		int askedNow = 0;
		for (int round = 0; round < 300; round++) {
			World world = new World();
			Loaded loaded = new Loaded();
			List<int[]> chunks = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				chunks.add(r.nextInt(8) == 0
						? new int[] {far[r.nextInt(2)], far[r.nextInt(2)]}
						: new int[] {r.nextInt(6) - 3, r.nextInt(6) - 3});
			}
			for (int x = -4; x <= 3; x++) {
				for (int z = -4; z <= 3; z++) {
					if (r.nextInt(4) != 0) loaded.chunks.add(PendingTicks.key(x, z));
				}
			}
			for (int a : far) {
				for (int b : far) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (r.nextInt(3) != 0) loaded.chunks.add(PendingTicks.key(a + dx, b + dz));
						}
					}
				}
			}
			// Some ticks already queued, some of them equal to parked ones.
			List<BlockEvent> parked = new ArrayList<>();
			for (int i = 0; i < 400; i++) {
				int[] c = chunks.get(r.nextInt(chunks.size()));
				int y = r.nextInt(10) == 0 ? edges[r.nextInt(edges.length)] : r.nextInt(128);
				BlockEvent e = new BlockEvent(c[0] * 16 + r.nextInt(16), y, c[1] * 16 + r.nextInt(16), 8 + r.nextInt(2));
				e.get(world.now - r.nextInt(50));
				if (r.nextInt(10) == 0) {
					world.schedule(new BlockEvent(e.x, e.y, e.z, e.blockId), 5);
				}
				parked.add(e);
				if (r.nextInt(8) == 0) {
					BlockEvent again = new BlockEvent(e.x, e.y, e.z, e.blockId);
					again.get(e.ticks + 3);
					parked.add(again);
				}
			}
			for (BlockEvent e : parked) world.parked.computeIfAbsent(PendingTicks.key(e.x >> 4, e.z >> 4), k -> new ArrayList<>()).add(e);
			world.now += 7;

			// What asking for each tick gives, worked out on what's there now.
			Map<Long, List<BlockEvent>> stays = new HashMap<>();
			List<BlockEvent> queued = new ArrayList<>(world.ordered);
			Set<BlockEvent> pending = new HashSet<>(world.pending);
			Loaded each = new Loaded();
			each.chunks.addAll(loaded.chunks);
			for (Map.Entry<Long, List<BlockEvent>> entry : world.parked.entrySet()) {
				for (BlockEvent e : entry.getValue()) {
					if (each.isLoaded(e.x - 8, e.y - 8, e.z - 8, e.x + 8, e.y + 8, e.z + 8)) {
						if (pending.add(e)) queued.add(e);
					} else {
						stays.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(e);
					}
				}
			}

			PendingTicks.sweep(world, world.now, loaded);
			askedBefore += each.asked;
			askedNow += loaded.asked;
			assertEquals(stays, world.parked);
			for (List<BlockEvent> list : stays.values()) {
				List<BlockEvent> left = world.parked.get(PendingTicks.key(list.get(0).x >> 4, list.get(0).z >> 4));
				for (int i = 0; i < list.size(); i++) assertSame(list.get(i), left.get(i));
			}
			assertEquals(queued.size(), world.ordered.size());
			assertEquals(world.ordered.size(), world.pending.size());
			Set<BlockEvent> byIdentity = Collections.newSetFromMap(new IdentityHashMap<>());
			byIdentity.addAll(queued);
			for (BlockEvent e : world.ordered) assertTrue(byIdentity.contains(e));
		}
		assertTrue(askedNow * 5 < askedBefore, askedNow + " asked now, " + askedBefore + " before");
	}
}
