package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. Acquiring never blocks: with one thread, a permit
 * that is not available now can never become available while the caller waits - see TLock
 * for the same reasoning. The count is still tracked, so tryAcquire answers truthfully.
 */
public class TSemaphore {

	private int permits;

	public TSemaphore(int permits) {
		this.permits = permits;
	}

	public TSemaphore(int permits, boolean fair) {
		this.permits = permits;
	}

	public void acquire() {
		--permits;
	}

	public void acquireUninterruptibly() {
		--permits;
	}

	public boolean tryAcquire() {
		if (permits > 0) {
			--permits;
			return true;
		}
		return false;
	}

	public boolean tryAcquire(long timeout, TimeUnit unit) {
		return tryAcquire();
	}

	public void release() {
		++permits;
	}

	public void release(int n) {
		permits += n;
	}

	public int availablePermits() {
		return permits;
	}

	public int drainPermits() {
		int n = permits;
		permits = 0;
		return n;
	}
}
