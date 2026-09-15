package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. await() cannot wait: with one thread, a latch that
 * has not reached zero can never reach it while the caller blocks - see TLock. It returns
 * instead, so the caller proceeds as if the work were done, which it is: everything the
 * executors run happens inline before the latch is ever awaited.
 */
public class TCountDownLatch {

	private long count;

	public TCountDownLatch(int count) {
		this.count = count;
	}

	public void await() {
	}

	public boolean await(long timeout, TimeUnit unit) {
		return count == 0;
	}

	public void countDown() {
		if (count > 0) {
			--count;
		}
	}

	public long getCount() {
		return count;
	}
}
