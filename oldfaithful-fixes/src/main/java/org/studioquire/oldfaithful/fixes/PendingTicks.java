package org.studioquire.oldfaithful.fixes;

import java.util.ArrayList;
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
 */
public final class PendingTicks {
	public static final String TAG = "TileTicks";
	public static final int SWEEP_TICKS = 20;

	private PendingTicks() {
	}

	/** World's queue, opened by the world mixin: pending ticks, the same ordered by due time, and the parked ones. */
	public interface Queue {
		Set<BlockEvent> oldfaithful$pending();

		TreeSet<BlockEvent> oldfaithful$ordered();

		/** Ticks waiting for their surroundings, by chunk ({@link #key}). */
		Map<Long, List<BlockEvent>> oldfaithful$parked();
	}

	/** A chunk's ticks from reading until it loads, and from unloading until it's written. */
	public interface Carrier {
		NbtList oldfaithful$ticks();

		void oldfaithful$setTicks(NbtList ticks);
	}

	public static long key(int chunkX, int chunkZ) {
		return (long) chunkX << 32 ^ chunkZ & 0xFFFFFFFFL;
	}

	/**
	 * The chunk's pending ticks, queued in due order and then parked, as a TileTicks list; {@code remove} takes them off
	 * the world.
	 */
	public static NbtList collect(World world, int chunkX, int chunkZ, boolean remove) {
		Queue queue = (Queue) world;
		long now = world.getTime();
		NbtList out = new NbtList();
		List<BlockEvent> taken = new ArrayList<>();
		for (BlockEvent e : queue.oldfaithful$ordered()) {
			if (e.x >> 4 == chunkX && e.z >> 4 == chunkZ) taken.add(e);
		}
		for (BlockEvent e : taken) out.add(write(e, now));
		if (remove) {
			for (BlockEvent e : taken) {
				queue.oldfaithful$ordered().remove(e);
				queue.oldfaithful$pending().remove(e);
			}
		}
		List<BlockEvent> parked = remove ? queue.oldfaithful$parked().remove(key(chunkX, chunkZ)) : queue.oldfaithful$parked().get(key(chunkX, chunkZ));
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
				queue(world, e);
			} catch (RuntimeException ignored) {
				// A field of the wrong type in an edited chunk: leave that tick out.
			}
		}
	}

	/** Parks a tick that came due without its surroundings loaded. */
	public static void park(World world, BlockEvent tick) {
		((Queue) world).oldfaithful$parked().computeIfAbsent(key(tick.x >> 4, tick.z >> 4), k -> new ArrayList<>()).add(tick);
	}

	/** Puts parked ticks whose surroundings are now loaded back on the queue, due now. */
	public static void sweep(World world) {
		Map<Long, List<BlockEvent>> parked = ((Queue) world).oldfaithful$parked();
		if (parked.isEmpty()) return;
		long now = world.getTime();
		for (Iterator<List<BlockEvent>> lists = parked.values().iterator(); lists.hasNext();) {
			List<BlockEvent> list = lists.next();
			for (Iterator<BlockEvent> it = list.iterator(); it.hasNext();) {
				BlockEvent e = it.next();
				if (!world.isRegionLoaded(e.x - 8, e.y - 8, e.z - 8, e.x + 8, e.y + 8, e.z + 8)) continue;
				it.remove();
				e.get(now);
				queue(world, e);
			}
			if (list.isEmpty()) lists.remove();
		}
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

	private static void queue(World world, BlockEvent e) {
		Queue queue = (Queue) world;
		if (queue.oldfaithful$pending().add(e)) queue.oldfaithful$ordered().add(e);
	}
}
