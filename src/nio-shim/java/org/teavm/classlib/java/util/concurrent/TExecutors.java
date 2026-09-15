package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. Every factory method here returns the same thing: an
 * executor that runs the task on the calling thread, immediately.
 *
 * That is not a shortcut, it is the only honest answer. A page has one thread and a web
 * worker has its own; neither can spawn more, so "hand this to a pool and carry on" has no
 * meaning. EaglercraftX already builds on that - EaglerServerContext passes Runnable::run
 * to WorldStem.load - and TCompletableFuture is written to match, so a future returned by
 * one of these is already complete and join() never waits.
 *
 * The cost is real and worth stating: work Minecraft expected to happen off the main thread
 * - chunk generation, resource reloading - happens inline, so the page blocks while it runs
 * instead of staying responsive.
 */
public final class TExecutors {

	private TExecutors() {
	}

	private static class InlineExecutorService implements ScheduledExecutorService {
		private boolean shutdown;

		@Override
		public void execute(Runnable command) {
			if (shutdown) {
				throw new java.util.concurrent.RejectedExecutionException("shut down");
			}
			command.run();
		}

		@Override
		public void shutdown() {
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow() {
			shutdown = true;
			return new ArrayList<>();
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return shutdown;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return true;
		}

		@Override
		public <T> Future<T> submit(Callable<T> task) {
			CompletableFuture<T> f = new CompletableFuture<>();
			try {
				f.complete(task.call());
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
			return f;
		}

		@Override
		public <T> Future<T> submit(Runnable task, T result) {
			return submit(() -> {
				task.run();
				return result;
			});
		}

		@Override
		public Future<?> submit(Runnable task) {
			return submit(task, null);
		}

		@Override
		public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
			List<Future<T>> out = new ArrayList<>();
			for (Callable<T> task : tasks) {
				out.add(submit(task));
			}
			return out;
		}

		@Override
		public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
			for (Callable<T> task : tasks) {
				try {
					return task.call();
				} catch (Throwable ignored) {
					// try the next one, as the real invokeAny does
				}
			}
			throw new IllegalStateException("Every task failed");
		}

		@Override
		public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
				TimeUnit unit) {
			return invokeAny(tasks);
		}

		@Override
		public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
				long timeout, TimeUnit unit) {
			return invokeAll(tasks);
		}

		/*
		 * Scheduling. There is no timer thread to hold a delayed task, so anything with a
		 * delay runs now and anything periodic runs exactly once. Minecraft uses these for
		 * background housekeeping, which is harmless to do eagerly; a caller that depended
		 * on the delay would see its work happen too early rather than not at all.
		 */

		@Override
		public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
			command.run();
			return null;
		}

		@Override
		public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
			try {
				callable.call();
			} catch (Throwable ignored) {
				// nothing can observe the result; a real ScheduledFuture would carry it
			}
			return null;
		}

		@Override
		public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
				long period, TimeUnit unit) {
			command.run();
			return null;
		}

		@Override
		public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
				long delay, TimeUnit unit) {
			command.run();
			return null;
		}
	}

	public static ExecutorService newFixedThreadPool(int n) {
		return new InlineExecutorService();
	}

	public static ExecutorService newFixedThreadPool(int n, ThreadFactory factory) {
		return new InlineExecutorService();
	}

	public static ExecutorService newCachedThreadPool() {
		return new InlineExecutorService();
	}

	public static ExecutorService newCachedThreadPool(ThreadFactory factory) {
		return new InlineExecutorService();
	}

	public static ExecutorService newSingleThreadExecutor() {
		return new InlineExecutorService();
	}

	public static ExecutorService newSingleThreadExecutor(ThreadFactory factory) {
		return new InlineExecutorService();
	}

	public static ExecutorService newWorkStealingPool() {
		return new InlineExecutorService();
	}

	public static ExecutorService newWorkStealingPool(int parallelism) {
		return new InlineExecutorService();
	}

	public static ScheduledExecutorService newScheduledThreadPool(int n) {
		return new InlineExecutorService();
	}

	public static ScheduledExecutorService newScheduledThreadPool(int n, ThreadFactory factory) {
		return new InlineExecutorService();
	}

	public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
		return new InlineExecutorService();
	}

	public static ScheduledExecutorService newSingleThreadScheduledExecutor(
			ThreadFactory factory) {
		return new InlineExecutorService();
	}

	public static ThreadFactory defaultThreadFactory() {
		return Thread::new;
	}
}
