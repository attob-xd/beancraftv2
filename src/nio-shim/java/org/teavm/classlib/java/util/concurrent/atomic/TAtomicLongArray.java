package org.teavm.classlib.java.util.concurrent.atomic;

/** See TAtomicReferenceArray. */
public class TAtomicLongArray {

	private final long[] array;

	public TAtomicLongArray(int length) {
		array = new long[length];
	}

	public TAtomicLongArray(long[] initial) {
		array = initial.clone();
	}

	public int length() {
		return array.length;
	}

	public long get(int i) {
		return array[i];
	}

	public void set(int i, long value) {
		array[i] = value;
	}

	public void lazySet(int i, long value) {
		array[i] = value;
	}

	public long getAndSet(int i, long value) {
		long old = array[i];
		array[i] = value;
		return old;
	}

	public boolean compareAndSet(int i, long expect, long update) {
		if (array[i] == expect) {
			array[i] = update;
			return true;
		}
		return false;
	}

	public long getAndIncrement(int i) {
		return array[i]++;
	}

	public long getAndDecrement(int i) {
		return array[i]--;
	}

	public long getAndAdd(int i, long delta) {
		long old = array[i];
		array[i] += delta;
		return old;
	}

	public long incrementAndGet(int i) {
		return ++array[i];
	}

	public long decrementAndGet(int i) {
		return --array[i];
	}

	public long addAndGet(int i, long delta) {
		return array[i] += delta;
	}
}
