package org.teavm.classlib.java.util.concurrent.atomic;

/**
 * TeaVM's class library has the scalar atomics (AtomicInteger, AtomicReference and the
 * field updaters) but none of the array ones. guava's LocalCache - the engine behind every
 * CacheBuilder in Minecraft - is built on AtomicReferenceArray, so without it a large part
 * of guava cannot be linked.
 *
 * A page runs on one thread, so "atomic" costs nothing here: there is no other thread to
 * interleave with, and every operation is already indivisible. The memory-ordering
 * guarantees are equally free. What the class must still provide is the exact API, which
 * is what this is.
 */
public class TAtomicReferenceArray<E> {

	private final Object[] array;

	public TAtomicReferenceArray(int length) {
		array = new Object[length];
	}

	public TAtomicReferenceArray(E[] initial) {
		array = new Object[initial.length];
		System.arraycopy(initial, 0, array, 0, initial.length);
	}

	public int length() {
		return array.length;
	}

	@SuppressWarnings("unchecked")
	public E get(int i) {
		return (E) array[i];
	}

	public void set(int i, E value) {
		array[i] = value;
	}

	public void lazySet(int i, E value) {
		array[i] = value;
	}

	@SuppressWarnings("unchecked")
	public E getAndSet(int i, E value) {
		E old = (E) array[i];
		array[i] = value;
		return old;
	}

	public boolean compareAndSet(int i, E expect, E update) {
		if (array[i] == expect) {
			array[i] = update;
			return true;
		}
		return false;
	}

	public boolean weakCompareAndSet(int i, E expect, E update) {
		return compareAndSet(i, expect, update);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < array.length; ++i) {
			sb.append(i == 0 ? "" : ", ").append(array[i]);
		}
		return sb.append(']').toString();
	}
}
