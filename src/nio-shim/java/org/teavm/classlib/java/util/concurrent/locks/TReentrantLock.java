package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/** See TLock. */
public class TReentrantLock implements Lock {

	private int holdCount;

	public TReentrantLock() {
	}

	public TReentrantLock(boolean fair) {
	}

	@Override
	public void lock() {
		++holdCount;
	}

	@Override
	public void lockInterruptibly() {
		++holdCount;
	}

	@Override
	public boolean tryLock() {
		++holdCount;
		return true;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit) {
		return tryLock();
	}

	@Override
	public void unlock() {
		if (holdCount > 0) {
			--holdCount;
		}
	}

	@Override
	public java.util.concurrent.locks.Condition newCondition() {
		throw new UnsupportedOperationException(
				"A condition cannot be awaited on a single thread; nothing could signal it");
	}

	public boolean isLocked() {
		return holdCount > 0;
	}

	public boolean isHeldByCurrentThread() {
		return holdCount > 0;
	}

	public int getHoldCount() {
		return holdCount;
	}
}
