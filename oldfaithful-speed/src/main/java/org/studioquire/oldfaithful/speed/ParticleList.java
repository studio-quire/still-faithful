package org.studioquire.oldfaithful.speed;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

/**
 * A list holding the same elements in the same order as an ArrayList given the same calls, for beta's particle layers,
 * whose two busy paths are quadratic on an ArrayList: at 4,000 particles each new one drops the oldest with
 * {@code remove(0)}, shifting the whole layer, and the tick's index loop removes the dead with {@code remove(i)} one
 * at a time, shifting the rest each time. Here the first element goes in O(1), and removals open a gap that moves
 * with them, so a pass of removals walking forward through the list moves each survivor once.
 *
 * <p>In {@code a}: from {@code head}, the first {@code front} elements, then {@code gap} empty slots, then the other
 * {@code back} elements, then free slots to the end. Every slot outside the elements is null.
 */
public final class ParticleList<E> extends AbstractList<E> implements RandomAccess {
	private Object[] a = new Object[16];
	private int head, front, gap, back;

	@Override
	public int size() {
		return front + back;
	}

	private int end() {
		return head + front + gap + back;
	}

	private int at(int index) {
		return index < front ? head + index : head + gap + index;
	}

	private void check(int index) {
		if (index < 0 || index >= size()) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
	}

	@SuppressWarnings("unchecked")
	@Override
	public E get(int index) {
		check(index);
		return (E) a[at(index)];
	}

	@SuppressWarnings("unchecked")
	@Override
	public E set(int index, E element) {
		check(index);
		int p = at(index);
		E old = (E) a[p];
		a[p] = element;
		return old;
	}

	@Override
	public boolean add(E element) {
		modCount++;
		if (back == 0) gap = 0; // a gap with nothing after it is free space
		if (end() == a.length) makeRoom();
		a[end()] = element;
		back++;
		return true;
	}

	@Override
	public void add(int index, E element) {
		if (index < 0 || index > size()) throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
		if (index == size()) {
			add(element);
			return;
		}
		modCount++;
		if (gap == 0) {
			if (end() == a.length) makeRoom();
			moveGap(index);
			System.arraycopy(a, head + front, a, head + front + 1, back);
			a[head + front] = element;
			front++;
		} else {
			moveGap(index);
			a[head + front] = element;
			front++;
			gap--;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public E remove(int index) {
		check(index);
		modCount++;
		int p;
		if (index == 0 && front > 0) {
			p = head++;
			front--;
		} else if (index == size() - 1 && back > 0) {
			p = end() - 1;
			back--;
		} else if (index == size() - 1) {
			p = head + front - 1;
			front--;
			gap = 0; // nothing after it
		} else {
			moveGap(index);
			p = head + front + gap;
			gap++;
			back--;
		}
		E old = (E) a[p];
		a[p] = null;
		if (size() == 0) head = front = gap = back = 0;
		return old;
	}

	@Override
	public void clear() {
		modCount++;
		Arrays.fill(a, head, end(), null);
		head = front = gap = back = 0;
	}

	/** Moves the gap so that {@code pos} elements come before it, nulling the slots it leaves. */
	private void moveGap(int pos) {
		if (gap > 0 && pos < front) {
			int n = front - pos;
			System.arraycopy(a, head + pos, a, head + pos + gap, n);
			Arrays.fill(a, head + pos, head + pos + Math.min(n, gap), null);
		} else if (gap > 0 && pos > front) {
			int n = pos - front;
			System.arraycopy(a, head + front + gap, a, head + front, n);
			Arrays.fill(a, Math.max(head + front + gap, head + pos), head + pos + gap, null);
		}
		back += front - pos;
		front = pos;
	}

	/** Closes the gap and the space before head, into an array with at least as much room again as there are elements. */
	private void makeRoom() {
		int size = size(), oldEnd = end();
		Object[] to = a.length >= 2 * size + 1 ? a : new Object[Math.max(16, 2 * size + 1)];
		System.arraycopy(a, head, to, 0, front);
		System.arraycopy(a, head + front + gap, to, front, back);
		if (to == a) Arrays.fill(a, size, oldEnd, null);
		a = to;
		head = 0;
		front = size;
		gap = 0;
		back = 0;
	}
}
