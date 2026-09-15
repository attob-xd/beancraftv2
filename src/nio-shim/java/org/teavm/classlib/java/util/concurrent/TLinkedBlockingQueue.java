package org.teavm.classlib.java.util.concurrent;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. The blocking half cannot block: take() and put()
 * would stop the only thread, with nothing left to fill or drain the queue. take() on an
 * empty queue therefore throws, naming the call site, instead of freezing the page.
 */
public class TLinkedBlockingQueue<E> extends AbstractQueue<E> implements BlockingQueue<E> {

	private final ArrayDeque<E> queue = new ArrayDeque<>();
	private final int capacity;

	public TLinkedBlockingQueue() {
		this(Integer.MAX_VALUE);
	}

	public TLinkedBlockingQueue(int capacity) {
		this.capacity = capacity;
	}

	public TLinkedBlockingQueue(Collection<? extends E> c) {
		this(Integer.MAX_VALUE);
		queue.addAll(c);
	}

	@Override
	public boolean offer(E e) {
		return queue.size() < capacity && queue.offer(e);
	}

	@Override
	public boolean offer(E e, long timeout, TimeUnit unit) {
		return offer(e);
	}

	@Override
	public void put(E e) {
		if (!offer(e)) {
			throw new IllegalStateException("This queue is full and nothing can drain it");
		}
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

	@Override
	public int remainingCapacity() {
		return capacity - queue.size();
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
