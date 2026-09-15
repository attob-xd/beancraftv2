package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Missing from TeaVM's class library. The copy-on-write behaviour exists so readers can
 * iterate without locking while a writer mutates; with one thread there is no such overlap,
 * so a plain ArrayList has the same observable semantics.
 */
public class TCopyOnWriteArrayList<E> extends ArrayList<E> {

	public TCopyOnWriteArrayList() {
	}

	public TCopyOnWriteArrayList(Collection<? extends E> c) {
		super(c);
	}

	public TCopyOnWriteArrayList(E[] initial) {
		for (E e : initial) {
			add(e);
		}
	}

	public boolean addIfAbsent(E e) {
		if (contains(e)) {
			return false;
		}
		add(e);
		return true;
	}

	public int addAllAbsent(Collection<? extends E> c) {
		int n = 0;
		for (E e : c) {
			if (addIfAbsent(e)) {
				++n;
			}
		}
		return n;
	}
}
