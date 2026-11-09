package org.studioquire.oldfaithful.speed;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.world.BlockEvent;

/**
 * A drop-in Set for World's scheduledUpdateSet, the HashSet beta keeps beside its due-time TreeSet of scheduled block
 * ticks to ask whether a block already has one queued.
 * <p>
 * BlockEvent.hashCode is x * 2^25 + z * 2^15 + y * 2^8 + blockId, so ticks 128 blocks apart along x share a hash code,
 * as do ticks one block apart in x and 1024 in z. HashMap's buckets turn into trees, and searching one compares
 * against every tick that shares the hash. This set hashes the four fields BlockEvent.equals compares, whole, and
 * probes an open table. Same equality (the argument's equals, as HashMap asks it), same answers.
 * <p>
 * The world's own thread only, as vanilla's. Iteration runs over a snapshot taken when the iterator is made, in another
 * order than HashSet's; its iterator's remove removes from the set. Vanilla iterates it only to move every tick by the
 * same time (World.synchronizeTimeAndUpdates), where order doesn't matter.
 */
public final class TickSet extends AbstractSet<Object> {
	private static final Object NULL = new Object();

	private Object[] table = new Object[64];
	private int size;

	/** MurmurHash3's 64-bit finaliser over a BlockEvent's x, y, z and block id; anything else by its own hashCode. */
	private static int hash(Object o) {
		long h;
		if (o instanceof BlockEvent) {
			BlockEvent e = (BlockEvent) o;
			h = e.x * 0x9E3779B97F4A7C15L + e.z * 0xC2B2AE3D27D4EB4FL + ((long) e.y << 32 | (e.blockId & 0xFFFFFFFFL));
		} else {
			h = o.hashCode();
		}
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (int) h;
	}

	private static Object mask(Object o) {
		return o == null ? NULL : o;
	}

	/** The slot in t holding o, or the empty slot where it would go (as -1 - slot). */
	private static int find(Object[] t, Object o) {
		int m = t.length - 1;
		int i = hash(o) & m;
		while (true) {
			Object e = t[i];
			if (e == null) return -1 - i;
			if (e == o || o.equals(e)) return i;
			i = (i + 1) & m;
		}
	}

	@Override
	public boolean contains(Object o) {
		return find(table, mask(o)) >= 0;
	}

	@Override
	public boolean add(Object o) {
		Object k = mask(o);
		int i = find(table, k);
		if (i >= 0) return false;
		table[-1 - i] = k;
		if (++size * 2 > table.length) grow();
		return true;
	}

	@Override
	public boolean remove(Object o) {
		int i = find(table, mask(o));
		if (i < 0) return false;
		deleteSlot(i);
		return true;
	}

	/** Backward-shift deletion, as ChunkMap's: later entries of the same probe run move up, so no tombstones. */
	private void deleteSlot(int i) {
		Object[] t = table;
		int m = t.length - 1;
		int gap = i;
		int j = i;
		while (true) {
			j = (j + 1) & m;
			Object e = t[j];
			if (e == null) break;
			int home = hash(e) & m;
			if (((j - home) & m) >= ((j - gap) & m)) {
				t[gap] = e;
				gap = j;
			}
		}
		t[gap] = null;
		size--;
	}

	private void grow() {
		Object[] old = table;
		Object[] t = new Object[old.length * 2];
		int m = t.length - 1;
		for (Object e : old) {
			if (e == null) continue;
			int i = hash(e) & m;
			while (t[i] != null) i = (i + 1) & m;
			t[i] = e;
		}
		table = t;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void clear() {
		table = new Object[64];
		size = 0;
	}

	@Override
	public Iterator<Object> iterator() {
		List<Object> snapshot = new ArrayList<>(size);
		for (Object e : table) {
			if (e != null) snapshot.add(e == NULL ? null : e);
		}
		Iterator<Object> it = snapshot.iterator();
		return new Iterator<Object>() {
			private Object current;
			private boolean removable;

			@Override
			public boolean hasNext() {
				return it.hasNext();
			}

			@Override
			public Object next() {
				current = it.next();
				removable = true;
				return current;
			}

			@Override
			public void remove() {
				if (!removable) throw new IllegalStateException();
				TickSet.this.remove(current);
				removable = false;
			}
		};
	}
}
