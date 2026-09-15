package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.TimeUnit;

/** Missing from TeaVM's class library; see TScheduledFuture. */
public interface TDelayed extends Comparable<TDelayed> {

	long getDelay(TimeUnit unit);
}
