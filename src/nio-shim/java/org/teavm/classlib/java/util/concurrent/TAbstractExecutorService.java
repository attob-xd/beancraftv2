package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library, and named as a supertype by guava's
 * AbstractListeningExecutorService - so the page could not even load without it, which is
 * the recurring shape of this whole class of problem.
 *
 * The submit methods run the task inline and hand back a future that is already complete;
 * see TExecutors for why that is the only meaning "async" can have on one thread.
 */
public abstract class TAbstractExecutorService implements ExecutorService {

	protected TAbstractExecutorService() {
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		CompletableFuture<T> f = new CompletableFuture<>();
		try {
			execute(() -> {
				try {
					f.complete(task.call());
				} catch (Throwable t) {
					f.completeExceptionally(t);
				}
			});
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
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
			TimeUnit unit) {
		return invokeAll(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
		for (Callable<T> task : tasks) {
			try {
				return task.call();
			} catch (Throwable ignored) {
				// try the next, as the real invokeAny does
			}
		}
		throw new IllegalStateException("Every task failed");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
		return invokeAny(tasks);
	}
}
