package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. Ordering is a real PriorityQueue's; the blocking
 * half cannot block, for the reason given in TLinkedBlockingQueue.
 */
public class TPriorityBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

	private final PriorityQueue<E> queue;

	public TPriorityBlockingQueue() {
		queue = new PriorityQueue<>();
	}

	public TPriorityBlockingQueue(int initialCapacity) {
		queue = new PriorityQueue<>(Math.max(1, initialCapacity));
	}

	public TPriorityBlockingQueue(int initialCapacity, Comparator<? super E> comparator) {
		queue = new PriorityQueue<>(Math.max(1, initialCapacity), comparator);
	}

	public TPriorityBlockingQueue(Collection<? extends E> c) {
		queue = new PriorityQueue<>(c);
	}

	@Override
	public boolean offer(E e) {
		return queue.offer(e);
	}

	@Override
	public boolean offer(E e, long timeout, TimeUnit unit) {
		return offer(e);
	}

	@Override
	public void put(E e) {
		queue.offer(e);
	}

	@Override
	public E poll() {
		return queue.poll();
	}

	@Override
	public E poll(long timeout, TimeUnit unit) {
		return queue.poll();
	}

	@Override
	public E take() {
		E e = queue.poll();
		if (e == null) {
			throw new IllegalStateException("take() on an empty queue would block the only"
					+ " thread there is; nothing could fill it");
		}
		return e;
	}

	@Override
	public E peek() {
		return queue.peek();
	}

	/** Unbounded, as the real PriorityBlockingQueue is. */
	@Override
	public int remainingCapacity() {
		return Integer.MAX_VALUE;
	}

	@Override
	public int drainTo(Collection<? super E> target) {
		return drainTo(target, Integer.MAX_VALUE);
	}

	@Override
	public int drainTo(Collection<? super E> target, int maxElements) {
		int n = 0;
		E e;
		while (n < maxElements && (e = queue.poll()) != null) {
			target.add(e);
			++n;
		}
		return n;
	}

	@Override
	public Iterator<E> iterator() {
		return queue.iterator();
	}

	@Override
	public int size() {
		return queue.size();
	}
}
