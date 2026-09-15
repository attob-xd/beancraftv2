package org.teavm.classlib.java.lang.management;

/** Missing from TeaVM's class library; see TThreadInfo for why it has to exist. */
public interface TThreadMXBean {

	TThreadInfo[] dumpAllThreads(boolean lockedMonitors, boolean lockedSynchronizers);

	int getThreadCount();

	long[] getAllThreadIds();
}
