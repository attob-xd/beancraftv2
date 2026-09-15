package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;

/**
 * Missing from TeaVM's class library, which has the blocking queues but not this one.
 * There is one thread, so an ArrayDeque behind it is already as concurrent as it needs to
 * be - see TLock for the same reasoning.
 */
public class TConcurrentLinkedQueue<E> extends AbstractQueue<E> {

	private final ArrayDeque<E> queue = new ArrayDeque<>();

	public TConcurrentLinkedQueue() {
	}

	public TConcurrentLinkedQueue(Collection<? extends E> c) {
		queue.addAll(c);
	}

	@Override
	public boolean offer(E e) {
		return queue.offer(e);
	}

	@Override
	public E poll() {
		return queue.poll();
	}

	@Override
	public E peek() {
		return queue.peek();
	}

	@Override
	public Iterator<E> iterator() {
		return queue.iterator();
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}
}
