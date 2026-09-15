package org.teavm.classlib.java.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. See {@link TThreadPoolExecutor} for why this constructs
 * rather than throwing: vanilla holds it in a static field
 * ({@code ServerSelectionList.THREAD_POOL}), so a constructor that refused took the whole
 * Multiplayer screen down with it.
 *
 * <p>Scheduling is where this differs most visibly from the real thing, and the difference is
 * stated rather than hidden: a task handed to {@code schedule} runs <b>now</b>, not after the
 * delay. There is no timer thread to wait on, and the alternative - dropping the task, or
 * blocking the one thread for the delay - would be worse in both directions. Callers in this
 * build schedule work they want done, not work whose timing they depend on; the server pinger
 * that owns this pool simply wants each ping to happen.
 *
 * <p>The repeating forms are the exception, because "run forever, now" is not a defensible
 * reading of {@code scheduleAtFixedRate}. They run the task once and return a future that is
 * already complete, which is the closest honest approximation: the work happens, and nothing
 * pretends a recurring timer exists.
 */
public class TScheduledThreadPoolExecutor extends TThreadPoolExecutor {

	public TScheduledThreadPoolExecutor(int corePoolSize) {
		super(corePoolSize);
	}

	public TScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
		super(corePoolSize);
	}

	/** Runs now; see the class comment on why the delay is not honoured. */
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		return completed(() -> {
			command.run();
			return null;
		});
	}

	/** Runs now; see the class comment on why the delay is not honoured. */
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		return completed(callable);
	}

	/** Runs once. There is no timer thread that could repeat it. */
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
			TimeUnit unit) {
		return schedule(command, initialDelay, unit);
	}

	/** Runs once. There is no timer thread that could repeat it. */
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
			TimeUnit unit) {
		return schedule(command, initialDelay, unit);
	}

	private <V> ScheduledFuture<V> completed(Callable<V> task) {
		CompletableFuture<V> future = new CompletableFuture<>();
		try {
			execute(() -> {
				try {
					future.complete(task.call());
				} catch (Throwable t) {
					future.completeExceptionally(t);
				}
			});
		} catch (Throwable t) {
			future.completeExceptionally(t);
		}
		return new CompletedScheduledFuture<>(future);
	}

	/**
	 * A {@link ScheduledFuture} over an already-finished CompletableFuture. Only the delay
	 * accessors are interesting: the task has run, so its remaining delay is zero.
	 */
	private static final class CompletedScheduledFuture<V> implements ScheduledFuture<V> {
		private final CompletableFuture<V> delegate;

		CompletedScheduledFuture(CompletableFuture<V> delegate) {
			this.delegate = delegate;
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return 0L;
		}

		@Override
		public int compareTo(java.util.concurrent.Delayed other) {
			return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			// Already run; there is nothing left to cancel.
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public V get() throws java.util.concurrent.ExecutionException, InterruptedException {
			return delegate.get();
		}

		@Override
		public V get(long timeout, TimeUnit unit)
				throws java.util.concurrent.ExecutionException, InterruptedException {
			return delegate.get();
		}
	}
}
