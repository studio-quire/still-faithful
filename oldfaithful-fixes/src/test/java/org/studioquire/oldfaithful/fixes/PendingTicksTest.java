package org.studioquire.oldfaithful.fixes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
 * mod did before it grouped the queue), through everything that happens to a world's queue, with the game's own
 * BlockEvents: ticks queued and run as beta does it, parked and put back, chunks saved and unloaded, the clock moved,
 * the set swapped under it.
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
		assertTrue(world.pending instanceof PendingTicks.Counted);
	}

	/** The point of the grouping: chunk after chunk from one pass over the queue, an unload's included. */
	@Test
	void groupsOnceWhileTheQueueStands() {
		Random r = new Random(7);
		World world = new World();
		for (int i = 0; i < 2000; i++) world.schedule(event(r), r.nextInt(40));
		PendingTicks.collect(world, world.now, 0, 0, false);
		PendingTicks.Counted counted = (PendingTicks.Counted) world.pending;
		Map<Long, List<BlockEvent>> grouping = counted.byChunk(world.ordered);
		for (int x = -3; x < 2; x++) {
			PendingTicks.collect(world, world.now, x, 0, false);
			assertEquals(world.wholeQueueRead(x, 1), read(PendingTicks.collect(world, world.now, x, 1, true)));
			assertSame(grouping, counted.byChunk(world.ordered));
		}

		world.schedule(new BlockEvent(1000, 64, 1000, 12), 5);
		assertNotSame(grouping, counted.byChunk(world.ordered));

		// One tick run and another queued, as a tick's block does: as many ticks as before, and not the same ones.
		grouping = counted.byChunk(world.ordered);
		world.now += 100;
		world.run(1, new Random(1));
		world.schedule(new BlockEvent(1001, 64, 1000, 12), 5);
		assertNotSame(grouping, counted.byChunk(world.ordered));

		// Another queue in the world's field, though it holds the same ticks.
		grouping = counted.byChunk(world.ordered);
		world.ordered = new TreeSet<>(world.ordered);
		assertNotSame(grouping, counted.byChunk(world.ordered));

		// The queue changed behind the set's back (beta stops at its next tick): a tick gone from it isn't written.
		grouping = counted.byChunk(world.ordered);
		BlockEvent first = world.ordered.first();
		world.ordered.remove(first);
		assertNotSame(grouping, counted.byChunk(world.ordered));
		assertEquals(world.wholeQueueRead(first.x >> 4, first.z >> 4), read(PendingTicks.collect(world, world.now, first.x >> 4, first.z >> 4, false)));
	}
}
