package org.teavm.classlib.java.util.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * TeaVM's class library has Executor but not ExecutorService. Minecraft holds one for its
 * background work - Util.backgroundExecutor(), the chunk pipeline, the resource reloader -
 * and EaglercraftX already answers those with an executor that runs the task inline (see
 * EaglerServerContext, which hands Runnable::run to WorldStem.load).
 *
 * Shutting one down therefore has nothing to interrupt, and awaiting termination has
 * nothing to wait for: every task submitted has already finished.
 */
public interface TExecutorService extends Executor {

	void shutdown();

	List<Runnable> shutdownNow();

	boolean isShutdown();

	boolean isTerminated();

	boolean awaitTermination(long timeout, TimeUnit unit);

	<T> Future<T> submit(Callable<T> task);

	<T> Future<T> submit(Runnable task, T result);

	Future<?> submit(Runnable task);

	<T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks);

	<T> T invokeAny(Collection<? extends Callable<T>> tasks);
}
