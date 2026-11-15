package org.studioquire.oldfaithful.fixes;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.BlockEvent;
import net.minecraft.world.World;

/**
 * #42: a world's pending block ticks (a repeater's next step, falling sand, flowing liquids) lived only in memory, so a
 * chunk unloaded or a world closed mid-change lost its next step; a repeater clock stopped dead. Each chunk now carries
 * its ticks: written into the chunk as {@code TileTicks} (the list release 1.3 added, same fields: block id, position
 * and the delay left), read back when the chunk loads, and taken off the world's queue when it unloads. b1.7.3 ignores
 * tags it doesn't know, so vanilla still loads the world (its ticks just stay lost there).
 * <p>
 * A tick that comes due while blocks within 8 of it aren't loaded (near the edge of the loaded world) was thrown away;
 * it now waits, parked off the queue so it never takes a turn from the 1,000 the world runs a tick, and goes back on the
 * queue once its surroundings are loaded (checked every {@link #SWEEP_TICKS}). A world's spawn area alone leaves tens
 * of thousands of liquid ticks at its edge, so retrying them on the queue itself would starve every other tick.
 * <p>
 * The queue is one list for the whole world, and new land leaves it long (some 400 ticks a chunk, a million after a
 * few thousand chunks made with nobody near). A chunk's ticks come from an index of the queue by chunk, kept up to date
 * as ticks are queued and run ({@link Indexed}), so saving or unloading a chunk costs its own ticks and no more. Reading
 * the whole queue for each chunk took a stop from seconds to most of a minute, and grouping the whole queue again
 * whenever it had changed cost every tick with an unload tens of milliseconds while a long queue drained.
 */
public final class PendingTicks {
	public static final String TAG = "TileTicks";
	public static final int SWEEP_TICKS = 20;

	private PendingTicks() {
	}

	/** World's queue, opened by the world mixin: pending ticks, the same ordered by due time, and the parked ones. */
	public interface Queue {
		Set<BlockEvent> oldfaithful$pending();

		/** Puts another set in the world's field: the same ticks, held by {@link Indexed}. */
		void oldfaithful$setPending(Set<BlockEvent> pending);

		TreeSet<BlockEvent> oldfaithful$ordered();

		/** Ticks waiting for their surroundings, by chunk ({@link #key}). */
		Map<Long, List<BlockEvent>> oldfaithful$parked();
	}

	/** A chunk's ticks from reading until it loads, and from unloading until it's written. */
	public interface Carrier {
		NbtList oldfaithful$ticks();

		void oldfaithful$setTicks(NbtList ticks);
	}

	/** World.isRegionLoaded, as {@link #sweep} asks it. */
	interface Region {
		boolean isLoaded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
	}

	/**
	 * The world's set of pending ticks, with an index of them by chunk kept beside it. Beta keeps the set and the queue
	 * in step (a tick goes on or off both together, and the game stops if their sizes ever differ), so the set's ticks
	 * by chunk are the queue's, and a chunk's kept in the queue's own order ({@link #order}) are what reading the
	 * queue for that chunk gives. The index is made from the queue in one pass the first time a chunk's ticks are asked
	 * for, and made again only if the world has another queue or the two have come apart in size (a change behind the
	 * set's back). The set itself is whatever the world had (beta's HashSet, the speed mod's TickSet) and answers as
	 * before; it's wrapped the first time a chunk's ticks are asked for, after any mod that swaps the set as the world is
	 * made.
	 */
	static final class Indexed extends AbstractSet<BlockEvent> {
		private final Set<BlockEvent> set;

		/** The queue's ticks by chunk ({@link #key}), or null until made. */
		private Map<Long, ChunkTicks> byChunk;
		// The queue the index was made from, and how many ticks the index holds.
		private TreeSet<BlockEvent> indexed;
		private int indexedSize;
		// The chunk last added to or taken from: ticks queued together are mostly one chunk's.
		private long lastKey;
		private ChunkTicks last;
		/** How many times the index has been made from the queue (for the test). */
		int builds;

		Indexed(Set<BlockEvent> set) {
			this.set = set;
		}

		/** The chunk's queued ticks in the queue's order, or null if it has none. */
		BlockEvent[] queued(TreeSet<BlockEvent> ordered, long key) {
			if (byChunk == null || indexed != ordered || indexedSize != ordered.size()) index(ordered);
			ChunkTicks ticks = byChunk.get(key);
			return ticks == null ? null : ticks.toArray();
		}

		private void index(TreeSet<BlockEvent> ordered) {
			byChunk = new HashMap<>();
			last = null;
			for (BlockEvent e : ordered) chunk(key(e), true).append(e);
			indexed = ordered;
			indexedSize = ordered.size();
			builds++;
		}

		/** Makes the index again from the queue when next asked. */
		void forget() {
			byChunk = null;
		}

		private ChunkTicks chunk(long key, boolean make) {
			if (last != null && lastKey == key) return last;
			ChunkTicks ticks = byChunk.get(key);
			if (ticks == null) {
				if (!make) return null;
				ticks = new ChunkTicks();
				byChunk.put(key, ticks);
			}
			lastKey = key;
			last = ticks;
			return ticks;
		}

		/** Takes a tick the set has just let go of out of the index. */
		private void unindex(Object o) {
			if (o instanceof BlockEvent) {
				long key = key((BlockEvent) o);
				ChunkTicks ticks = chunk(key, false);
				if (ticks != null && ticks.remove((BlockEvent) o)) {
					indexedSize--;
					if (ticks.size == 0) {
						byChunk.remove(key);
						last = null;
					}
					return;
				}
			}
			// The index didn't hold what the set did.
			forget();
		}

		@Override
		public boolean add(BlockEvent e) {
			boolean added = set.add(e);
			if (added && byChunk != null) {
				chunk(key(e), true).add(e);
				indexedSize++;
			}
			return added;
		}

		@Override
		public boolean remove(Object o) {
			boolean removed = set.remove(o);
			if (removed && byChunk != null) unindex(o);
			return removed;
		}

		@Override
		public void clear() {
			set.clear();
			if (byChunk != null) {
				byChunk.clear();
				indexedSize = 0;
				last = null;
			}
		}

		@Override
		public boolean contains(Object o) {
			return set.contains(o);
		}

		@Override
		public int size() {
			return set.size();
		}

		@Override
		public Iterator<BlockEvent> iterator() {
			Iterator<BlockEvent> it = set.iterator();
			return new Iterator<BlockEvent>() {
				private BlockEvent current;

				@Override
				public boolean hasNext() {
					return it.hasNext();
				}

				@Override
				public BlockEvent next() {
					return current = it.next();
				}

				@Override
				public void remove() {
					it.remove();
					if (byChunk != null) unindex(current);
				}
			};
		}
	}

	/**
	 * The queue's order, as its TreeSet asks it of BlockEvent: due time, then the order the ticks were made in, which
	 * is a total order (no two ticks compare equal).
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	static int order(BlockEvent a, BlockEvent b) {
		return ((Comparable) a).compareTo(b);
	}

	/**
	 * One chunk's queued ticks in the queue's order, in an array with room kept at both ends: the queue runs ticks from
	 * the front and most new ones go near the back, so neither moves the rest, however many the chunk holds.
	 */
	static final class ChunkTicks {
		private BlockEvent[] a = new BlockEvent[8];
		private int head;
		int size;

		/** Where e is, or -1 - where it would go. */
		private int search(BlockEvent e) {
			int lo = 0;
			int hi = size - 1;
			while (lo <= hi) {
				int mid = (lo + hi) >>> 1;
				int c = order(e, a[head + mid]);
				if (c > 0) {
					lo = mid + 1;
				} else if (c < 0) {
					hi = mid - 1;
				} else {
					return mid;
				}
			}
			return -1 - lo;
		}

		/** Adds a tick that comes after every one here. */
		void append(BlockEvent e) {
			insert(size, e);
		}

		void add(BlockEvent e) {
			int i = search(e);
			insert(i < 0 ? -1 - i : i, e);
		}

		/** Takes off e, or else the tick equal to it (the set removes by equality); false if neither is here. */
		boolean remove(BlockEvent e) {
			int i = search(e);
			if (i < 0 || a[head + i] != e) {
				i = -1;
				for (int j = 0; j < size; j++) {
					if (e.equals(a[head + j])) {
						i = j;
						break;
					}
				}
				if (i < 0) return false;
			}
			delete(i);
			return true;
		}

		BlockEvent[] toArray() {
			return Arrays.copyOfRange(a, head, head + size);
		}

		private void insert(int i, BlockEvent e) {
			boolean front = i < size - i;
			if (front ? head == 0 : head + size == a.length) place(size * 2 >= a.length ? a.length * 2 : a.length);
			if (front) {
				System.arraycopy(a, head, a, head - 1, i);
				head--;
			} else {
				System.arraycopy(a, head + i, a, head + i + 1, size - i);
			}
			a[head + i] = e;
			size++;
		}

		private void delete(int i) {
			if (i < size - 1 - i) {
				System.arraycopy(a, head, a, head + 1, i);
				a[head++] = null;
			} else {
				System.arraycopy(a, head + i + 1, a, head + i, size - 1 - i);
				a[head + size - 1] = null;
			}
			size--;
			// A chunk that held many and holds few gives the room back.
			if (a.length > 32 && size * 8 < a.length) place(a.length / 2);
		}

		/** The ticks moved to the middle of an array of that length, with room on both sides. */
		private void place(int length) {
			BlockEvent[] to = new BlockEvent[length];
			int at = (length - size) / 2;
			System.arraycopy(a, head, to, at, size);
			a = to;
			head = at;
		}
	}

	private static Indexed indexed(Queue queue) {
		Set<BlockEvent> pending = queue.oldfaithful$pending();
		if (pending instanceof Indexed) return (Indexed) pending;
		Indexed indexed = new Indexed(pending);
		queue.oldfaithful$setPending(indexed);
		return indexed;
	}

	public static long key(int chunkX, int chunkZ) {
		return (long) chunkX << 32 ^ chunkZ & 0xFFFFFFFFL;
	}

	private static long key(BlockEvent e) {
		return key(e.x >> 4, e.z >> 4);
	}

	/**
	 * The chunk's pending ticks, queued in due order and then parked, as a TileTicks list; {@code remove} takes them off
	 * the world.
	 */
	public static NbtList collect(World world, int chunkX, int chunkZ, boolean remove) {
		return collect((Queue) world, world.getTime(), chunkX, chunkZ, remove);
	}

	static NbtList collect(Queue queue, long now, int chunkX, int chunkZ, boolean remove) {
		long key = key(chunkX, chunkZ);
		NbtList out = new NbtList();
		Indexed pending = indexed(queue);
		TreeSet<BlockEvent> ordered = queue.oldfaithful$ordered();
		BlockEvent[] queued = pending.queued(ordered, key);
		if (queued != null) {
			for (BlockEvent e : queued) out.add(write(e, now));
			if (remove) {
				boolean all = true;
				for (BlockEvent e : queued) all &= ordered.remove(e) & pending.remove(e);
				// The index held a tick the queue or the set didn't: it's made again from the queue next time.
				if (!all) pending.forget();
			}
		}
		List<BlockEvent> parked = remove ? queue.oldfaithful$parked().remove(key) : queue.oldfaithful$parked().get(key);
		if (parked != null) {
			// Overdue: due as soon as they can run.
			for (BlockEvent e : parked) out.add(write(e, now));
		}
		return out;
	}

	private static NbtCompound write(BlockEvent e, long now) {
		NbtCompound t = new NbtCompound();
		t.putInt("i", e.blockId);
		t.putInt("x", e.x);
		t.putInt("y", e.y);
		t.putInt("z", e.z);
		t.putInt("t", (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, e.ticks - now)));
		return t;
	}

	/**
	 * Puts a chunk's saved ticks back on the world's queue, each due after the delay it had left. Entries that aren't
	 * well-formed or lie outside the chunk are skipped: a tick only runs if its block is still there anyway.
	 */
	public static void restore(World world, int chunkX, int chunkZ, NbtList ticks) {
		long now = world.getTime();
		for (int i = 0; i < ticks.size(); i++) {
			try {
				NbtElement element = ticks.get(i);
				if (!(element instanceof NbtCompound)) continue;
				NbtCompound t = (NbtCompound) element;
				BlockEvent e = new BlockEvent(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("i"));
				if (e.blockId <= 0 || e.blockId >= 256 || e.x >> 4 != chunkX || e.z >> 4 != chunkZ) continue;
				e.get(now + t.getInt("t"));
				queue((Queue) world, e);
			} catch (RuntimeException ignored) {
				// A field of the wrong type in an edited chunk: leave that tick out.
			}
		}
	}

	/** Parks a tick that came due without its surroundings loaded. */
	public static void park(World world, BlockEvent tick) {
		((Queue) world).oldfaithful$parked().computeIfAbsent(key(tick), k -> new ArrayList<>()).add(tick);
	}

	/** Puts parked ticks whose surroundings are now loaded back on the queue, due now. */
	public static void sweep(World world) {
		sweep((Queue) world, world.getTime(), world::isRegionLoaded);
	}

	/**
	 * The blocks within 8 of a tick span two chunks each way, which two going by the quarter of its own chunk it's in,
	 * and nothing loads or unloads during a sweep. So a chunk's parked ticks ask the world at most once a quarter, the
	 * first tick in each quarter asking for all of them; a tick whose span is any other (only at the far edges of int)
	 * asks for itself.
	 */
	static void sweep(Queue queue, long now, Region region) {
		Map<Long, List<BlockEvent>> parked = queue.oldfaithful$parked();
		if (parked.isEmpty()) return;
		// Per quarter of the chunk: 0 not asked yet, 1 loaded, 2 not.
		byte[] quarters = new byte[4];
		for (Iterator<Map.Entry<Long, List<BlockEvent>>> lists = parked.entrySet().iterator(); lists.hasNext();) {
			Map.Entry<Long, List<BlockEvent>> entry = lists.next();
			long key = entry.getKey();
			int chunkX = (int) (key >> 32);
			int chunkZ = (int) key;
			Arrays.fill(quarters, (byte) 0);
			List<BlockEvent> list = entry.getValue();
			for (Iterator<BlockEvent> it = list.iterator(); it.hasNext();) {
				BlockEvent e = it.next();
				if (!surroundingsLoaded(e, chunkX, chunkZ, quarters, region)) continue;
				it.remove();
				e.get(now);
				queue(queue, e);
			}
			if (list.isEmpty()) lists.remove();
		}
	}

	/** The region check processScheduledTicks makes for a tick, the 17x17x17 blocks around it. */
	private static boolean surroundingsLoaded(BlockEvent e, int chunkX, int chunkZ, byte[] quarters, Region region) {
		int minX = e.x - 8;
		int minY = e.y - 8;
		int minZ = e.z - 8;
		int maxX = e.x + 8;
		int maxY = e.y + 8;
		int maxZ = e.z + 8;
		// World.isRegionLoaded's own height test, before it looks at any chunk.
		if (maxY < 0 || minY >= 128) return false;
		int qx = (minX >> 4) - (chunkX - 1);
		int qz = (minZ >> 4) - (chunkZ - 1);
		if ((qx | qz) >>> 1 != 0 || maxX >> 4 != (minX >> 4) + 1 || maxZ >> 4 != (minZ >> 4) + 1) {
			return region.isLoaded(minX, minY, minZ, maxX, maxY, maxZ);
		}
		int q = qx + 2 * qz;
		if (quarters[q] == 0) quarters[q] = region.isLoaded(minX, minY, minZ, maxX, maxY, maxZ) ? (byte) 1 : (byte) 2;
		return quarters[q] == 1;
	}

	/** A chunk's TileTicks as read, or null if it has none (or something else under that name). */
	public static NbtList read(NbtCompound chunk) {
		if (!chunk.contains(TAG)) return null;
		try {
			return chunk.getList(TAG);
		} catch (ClassCastException e) {
			return null;
		}
	}

	private static void queue(Queue queue, BlockEvent e) {
		if (queue.oldfaithful$pending().add(e)) queue.oldfaithful$ordered().add(e);
	}
}
