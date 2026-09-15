package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * TeaVM's class library stops at Executor, Callable and the blocking queues; Future is not
 * there. See TCompletableFuture for the rest of the story.
 */
public interface TFuture<V> {

	boolean cancel(boolean mayInterruptIfRunning);

	boolean isCancelled();

	boolean isDone();

	V get() throws InterruptedException, ExecutionException;

	V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException;
}
