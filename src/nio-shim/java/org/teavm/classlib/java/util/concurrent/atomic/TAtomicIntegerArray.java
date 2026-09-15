package org.teavm.classlib.java.util.concurrent.atomic;

/** See TAtomicReferenceArray. */
public class TAtomicIntegerArray {

	private final int[] array;

	public TAtomicIntegerArray(int length) {
		array = new int[length];
	}

	public TAtomicIntegerArray(int[] initial) {
		array = initial.clone();
	}

	public int length() {
		return array.length;
	}

	public int get(int i) {
		return array[i];
	}

	public void set(int i, int value) {
		array[i] = value;
	}

	public void lazySet(int i, int value) {
		array[i] = value;
	}

	public int getAndSet(int i, int value) {
		int old = array[i];
		array[i] = value;
		return old;
	}

	public boolean compareAndSet(int i, int expect, int update) {
		if (array[i] == expect) {
			array[i] = update;
			return true;
		}
		return false;
	}

	public int getAndIncrement(int i) {
		return array[i]++;
	}

	public int getAndDecrement(int i) {
		return array[i]--;
	}

	public int getAndAdd(int i, int delta) {
		int old = array[i];
		array[i] += delta;
		return old;
	}

	public int incrementAndGet(int i) {
		return ++array[i];
	}

	public int decrementAndGet(int i) {
		return --array[i];
	}

	public int addAndGet(int i, int delta) {
		return array[i] += delta;
	}
}
