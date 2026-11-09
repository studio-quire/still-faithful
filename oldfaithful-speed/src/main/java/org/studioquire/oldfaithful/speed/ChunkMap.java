package org.studioquire.oldfaithful.speed;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A drop-in Map for b1.7.3's chunk maps (ChunkCache's and the server's ServerChunkCache's Integer keys,
 * MultiplayerChunkCache's ChunkPos keys).
 * <p>
 * All key on ChunkPos.hashCode, which packs x into the high half and z into the low half; HashMap folds the halves
 * together with xor, so chunks along a diagonal share a bucket and the buckets turn into trees. This map mixes the
 * same hashCode properly and probes an open table, and remembers the last key it found. Same keys, same equality,
 * same answers: only the lookup is faster.
 * <p>
 * Writes come from the game thread only (the server thread on a server), as vanilla's. Other threads may read (a threaded minimap, say), as HashMap
 * tolerates in practice: a reader can miss or see a stale entry while the map changes, but never pairs one chunk's key
 * with another's value. Each key and value travel together in one immutable {@link Entry}, the table is replaced
 * whole when it grows, and the last-hit cache is one Entry reference.
 * <p>
 * Views (entrySet, keySet, values) iterate a snapshot taken when the iterator is made; their iterators' remove
 * removes from the map. Vanilla never iterates these maps.
 */
public final class ChunkMap<K, V> extends AbstractMap<K, V> {
	private static final Object NULL_KEY = new Object();

	private static final class Entry {
		final Object key;
		final Object value;

		Entry(Object key, Object value) {
			this.key = key;
			this.value = value;
		}
	}

	private Entry[] table = new Entry[64];
	private int size;
	private Entry last;

	/** MurmurHash3's finaliser: every bit of the hashCode reaches the low bits the table indexes by. */
	private static int mix(int h) {
		h ^= h >>> 16;
		h *= 0x85EBCA6B;
		h ^= h >>> 13;
		h *= 0xC2B2AE35;
		return h ^ (h >>> 16);
	}

	private static Object mask(Object key) {
		return key == null ? NULL_KEY : key;
	}

	/** The slot in t holding key, or the empty slot where it would go (as -1 - slot). */
	private static int find(Entry[] t, Object k) {
		int m = t.length - 1;
		int i = mix(k.hashCode()) & m;
		while (true) {
			Entry e = t[i];
			if (e == null) return -1 - i;
			if (e.key == k || e.key.equals(k)) return i;
			i = (i + 1) & m;
		}
	}

	/** The entry for key, read once per slot, so a reader racing the writer never pairs a key with another's value. */
	private static Entry lookup(Entry[] t, Object k) {
		int m = t.length - 1;
		int i = mix(k.hashCode()) & m;
		while (true) {
			Entry e = t[i];
			if (e == null) return null;
			if (e.key == k || e.key.equals(k)) return e;
			i = (i + 1) & m;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public V get(Object key) {
		Object k = mask(key);
		Entry l = last;
		if (l != null && (l.key == k || l.key.equals(k))) return (V) l.value;
		Entry e = lookup(table, k);
		if (e == null) return null;
		last = e;
		return (V) e.value;
	}

	@Override
	public boolean containsKey(Object key) {
		Object k = mask(key);
		Entry l = last;
		if (l != null && (l.key == k || l.key.equals(k))) return true;
		return lookup(table, k) != null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V put(K key, V value) {
		Object k = mask(key);
		last = null;
		Entry[] t = table;
		int i = find(t, k);
		if (i >= 0) {
			V old = (V) t[i].value;
			t[i] = new Entry(k, value);
			return old;
		}
		t[-1 - i] = new Entry(k, value);
		if (++size * 2 > t.length) grow();
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public V remove(Object key) {
		Object k = mask(key);
		last = null;
		int i = find(table, k);
		if (i < 0) return null;
		V old = (V) table[i].value;
		deleteSlot(i);
		return old;
	}

	/** Backward-shift deletion: later entries of the same probe run move up, so no tombstones are needed. */
	private void deleteSlot(int i) {
		Entry[] t = table;
		int m = t.length - 1;
		int gap = i;
		int j = i;
		while (true) {
			j = (j + 1) & m;
			Entry e = t[j];
			if (e == null) break;
			int home = mix(e.key.hashCode()) & m;
			// Move e into the gap if its home slot isn't cyclically within (gap, j].
			if (((j - home) & m) >= ((j - gap) & m)) {
				t[gap] = e;
				gap = j;
			}
		}
		t[gap] = null;
		size--;
	}

	/** Builds the larger table completely, then publishes it in one write. */
	private void grow() {
		Entry[] old = table;
		Entry[] t = new Entry[old.length * 2];
		int m = t.length - 1;
		for (Entry e : old) {
			if (e == null) continue;
			int i = mix(e.key.hashCode()) & m;
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
		last = null;
		table = new Entry[64];
		size = 0;
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K, V>>() {
			@Override
			@SuppressWarnings("unchecked")
			public Iterator<Map.Entry<K, V>> iterator() {
				List<Map.Entry<K, V>> snapshot = new ArrayList<>(size);
				for (Entry e : table) {
					if (e != null) snapshot.add(new SimpleEntry<K, V>((K) (e.key == NULL_KEY ? null : e.key), (V) e.value) {
						@Override
						public V setValue(V value) {
							super.setValue(value);
							return ChunkMap.this.put(getKey(), value);
						}
					});
				}
				Iterator<Map.Entry<K, V>> it = snapshot.iterator();
				return new Iterator<Map.Entry<K, V>>() {
					private Map.Entry<K, V> current;

					@Override
					public boolean hasNext() {
						return it.hasNext();
					}

					@Override
					public Map.Entry<K, V> next() {
						return current = it.next();
					}

					@Override
					public void remove() {
						if (current == null) throw new IllegalStateException();
						ChunkMap.this.remove(current.getKey());
						current = null;
					}
				};
			}

			@Override
			public int size() {
				return size;
			}
		};
	}
}
