package org.teavm.classlib.java.util.concurrent;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Missing from TeaVM's class library. Same reasoning as TCopyOnWriteArrayList: copy-on-write
 * exists so a reader can iterate without locking while a writer mutates, and with one thread
 * there is no such overlap - so an ordinary set has the same observable behaviour.
 *
 * LinkedHashSet rather than HashSet because the real one iterates in insertion order, being
 * backed by an array, and code that was written against it may rely on that.
 *
 * Nothing calls this. It is here because guava's Sets.newCopyOnWriteArraySet names it as a
 * return type, and TeaVM writes return types into class metadata: touching Sets' metadata
 * without it raised "ReferenceError: CopyOnWriteArraySet is not defined" at run time, which
 * vanilla caught and reported as the far less helpful "Couldn't load pack metadata".
 */
public class TCopyOnWriteArraySet<E> extends LinkedHashSet<E> {

	public TCopyOnWriteArraySet() {
	}

	public TCopyOnWriteArraySet(Collection<? extends E> c) {
		super(c);
	}
}
