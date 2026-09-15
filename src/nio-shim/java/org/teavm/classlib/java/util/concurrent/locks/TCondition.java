package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. Awaiting a condition on the only thread there is
 * would stop the page with nothing left able to signal it, so every await returns at once -
 * a caller that re-checks its predicate in a loop, which is how conditions are meant to be
 * used, makes progress rather than hanging.
 */
public interface TCondition {

	void await();

	boolean await(long time, TimeUnit unit);

	void awaitUninterruptibly();

	long awaitNanos(long nanosTimeout);

	void signal();

	void signalAll();
}
