package org.teavm.classlib.java.util.concurrent;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Missing from TeaVM's class library. A skip list exists so readers and writers can share a
 * sorted map without locking; with one thread there is nothing to share it with, so a
 * TreeMap has the same observable behaviour - sorted, and every operation indivisible
 * because nothing can interleave. See TLock.
 */
public class TConcurrentSkipListMap<K, V> extends TreeMap<K, V> {

	public TConcurrentSkipListMap() {
	}

	public TConcurrentSkipListMap(Comparator<? super K> comparator) {
		super(comparator);
	}

	public TConcurrentSkipListMap(Map<? extends K, ? extends V> m) {
		super(m);
	}
}
