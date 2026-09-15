package org.teavm.classlib.java.lang.management;

/**
 * Missing from TeaVM's class library; see TThreadInfo for why it has to exist.
 *
 * The bean reports the one thread the page has. Reporting zero threads would be a lie in the
 * other direction - the code asking is trying to describe what is running, and something is.
 */
public class TManagementFactory {

	private static final TThreadMXBean THREADS = new TThreadMXBean() {
		@Override
		public TThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers) {
			return new TThreadInfo[] { new TThreadInfo(Thread.currentThread().getName()) };
		}

		@Override
		public int getThreadCount() {
			return 1;
		}

		@Override
		public long[] getAllThreadIds() {
			return new long[] { 0L };
		}
	};

	public static TThreadMXBean getThreadMXBean() {
		return THREADS;
	}
}
