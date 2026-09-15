package org.teavm.classlib.java.util.concurrent;

/** Missing from TeaVM's class library; a hand-off queue needs two threads to hand between. */
public abstract class TSynchronousQueue<E> {

	protected TSynchronousQueue() {
		throw new UnsupportedOperationException(
				"A SynchronousQueue hands off between two threads; there is only one here");
	}
}
