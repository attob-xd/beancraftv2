package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * TeaVM's class library has no CompletableFuture - java.util.concurrent stops at Executor,
 * Callable, ConcurrentHashMap and the blocking queues. 1.18.2 is built on it: 145 vanilla
 * classes use it, including the chunk pipeline, WorldStem.load, the resource reloader and
 * every server task queue. Without it the port cannot load a world at all.
 *
 * This is a single-threaded implementation, which is the honest shape for a browser: there
 * is one thread per page and one per web worker, and nothing to run a task *on* while the
 * caller waits. So an executor passed to supplyAsync, runAsync or any of the *Async
 * methods is used - the task really is handed to it - but EaglercraftX passes
 * Runnable::run everywhere (see EaglerServerContext), which means the work happens inline
 * and the future is already complete when it is returned. join() and get() then never
 * block, because there is never an outstanding task to wait for.
 *
 * The consequence to know about: a future completed by something *outside* this model - a
 * reply that only arrives on a later frame - cannot be joined. join() on an incomplete
 * future throws rather than deadlocking the page, so that shows up as a stack trace at the
 * call site instead of a frozen tab.
 *
 * The 30 members below are the ones the vanilla jar actually calls, measured with
 * tools/scan_members.py.
 */
public class TCompletableFuture<T> implements TCompletionStage<T>, TFuture<T> {

	private T result;
	private Throwable exception;
	private boolean done;
	private boolean cancelled;
	private final List<Runnable> callbacks = new ArrayList<>();

	public TCompletableFuture() {
	}

	public static <U> TCompletableFuture<U> completedFuture(U value) {
		TCompletableFuture<U> f = new TCompletableFuture<>();
		f.complete(value);
		return f;
	}

	public static <U> TCompletableFuture<U> failedFuture(Throwable ex) {
		TCompletableFuture<U> f = new TCompletableFuture<>();
		f.completeExceptionally(ex);
		return f;
	}

	public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier) {
		return supplyAsync(supplier, Runnable::run);
	}

	public static <U> TCompletableFuture<U> supplyAsync(Supplier<U> supplier, Executor executor) {
		TCompletableFuture<U> f = new TCompletableFuture<>();
		executor.execute(() -> {
			try {
				f.complete(supplier.get());
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
		});
		return f;
	}

	public static TCompletableFuture<Void> runAsync(Runnable action) {
		return runAsync(action, Runnable::run);
	}

	public static TCompletableFuture<Void> runAsync(Runnable action, Executor executor) {
		TCompletableFuture<Void> f = new TCompletableFuture<>();
		executor.execute(() -> {
			try {
				action.run();
				f.complete(null);
			} catch (Throwable t) {
				f.completeExceptionally(t);
			}
		});
		return f;
	}

	public static TCompletableFuture<Void> allOf(TCompletableFuture<?>... futures) {
		TCompletableFuture<Void> out = new TCompletableFuture<>();
		int[] remaining = { futures.length };
		if (futures.length == 0) {
			out.complete(null);
			return out;
		}
		for (TCompletableFuture<?> f : futures) {
			f.onSettled(() -> {
				if (f.exception != null) {
					out.completeExceptionally(f.exception);
				} else if (--remaining[0] == 0) {
					out.complete(null);
				}
			});
		}
		return out;
	}

	public static TCompletableFuture<Object> anyOf(TCompletableFuture<?>... futures) {
		TCompletableFuture<Object> out = new TCompletableFuture<>();
		for (TCompletableFuture<?> f : futures) {
			f.onSettled(() -> {
				if (f.exception != null) {
					out.completeExceptionally(f.exception);
				} else {
					out.complete(f.result);
				}
			});
		}
		return out;
	}

	public boolean complete(T value) {
		if (done) {
			return false;
		}
		result = value;
		settle();
		return true;
	}

	public boolean completeExceptionally(Throwable ex) {
		if (done) {
			return false;
		}
		exception = ex;
		settle();
		return true;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		if (done) {
			return false;
		}
		cancelled = true;
		exception = new CancellationException();
		settle();
		return true;
	}

	private void settle() {
		done = true;
		List<Runnable> pending = new ArrayList<>(callbacks);
		callbacks.clear();
		for (Runnable r : pending) {
			r.run();
		}
	}

	private void onSettled(Runnable action) {
		if (done) {
			action.run();
		} else {
			callbacks.add(action);
		}
	}

	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	public boolean isCompletedExceptionally() {
		return exception != null;
	}

	@Override
	public T get() throws ExecutionException {
		requireDone("get()");
		if (exception != null) {
			throw new ExecutionException(exception);
		}
		return result;
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws ExecutionException {
		return get();
	}

	public T join() {
		requireDone("join()");
		if (exception != null) {
			throw exception instanceof CompletionException ? (CompletionException) exception
					: new CompletionException(exception);
		}
		return result;
	}

	public T getNow(T valueIfAbsent) {
		if (!done) {
			return valueIfAbsent;
		}
		if (exception != null) {
			throw exception instanceof CompletionException ? (CompletionException) exception
					: new CompletionException(exception);
		}
		return result;
	}

	/**
	 * There is no second thread to complete this future while we wait, so waiting would
	 * hang the page. Failing here names the call site instead.
	 */
	private void requireDone(String what) {
		if (!done) {
			throw new IllegalStateException(what + " on a CompletableFuture that is not"
					+ " complete; the browser has one thread, so nothing can complete it"
					+ " while this call blocks");
		}
	}

	private <U> TCompletableFuture<U> derive(BiConsumer<TCompletableFuture<U>, Throwable> body) {
		TCompletableFuture<U> out = new TCompletableFuture<>();
		onSettled(() -> {
			try {
				body.accept(out, exception);
			} catch (Throwable t) {
				out.completeExceptionally(t);
			}
		});
		return out;
	}

	@Override
	public <U> TCompletableFuture<U> thenApply(Function<? super T, ? extends U> fn) {
		return derive((out, err) -> {
			if (err != null) {
				out.completeExceptionally(err);
			} else {
				out.complete(fn.apply(result));
			}
		});
	}

	@Override
	public <U> TCompletableFuture<U> thenApplyAsync(Function<? super T, ? extends U> fn,
			Executor executor) {
		return thenApply(fn);
	}

	@Override
	public TCompletableFuture<Void> thenAccept(Consumer<? super T> action) {
		return derive((out, err) -> {
			if (err != null) {
				out.completeExceptionally(err);
			} else {
				action.accept(result);
				out.complete(null);
			}
		});
	}

	@Override
	public TCompletableFuture<Void> thenAcceptAsync(Consumer<? super T> action, Executor executor) {
		return thenAccept(action);
	}

	@Override
	public TCompletableFuture<Void> thenRun(Runnable action) {
		return derive((out, err) -> {
			if (err != null) {
				out.completeExceptionally(err);
			} else {
				action.run();
				out.complete(null);
			}
		});
	}

	@Override
	public TCompletableFuture<Void> thenRunAsync(Runnable action, Executor executor) {
		return thenRun(action);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <U> TCompletableFuture<U> thenCompose(
			Function<? super T, ? extends TCompletionStage<U>> fn) {
		return derive((out, err) -> {
			if (err != null) {
				out.completeExceptionally(err);
				return;
			}
			TCompletableFuture<U> next = (TCompletableFuture<U>) fn.apply(result);
			next.onSettled(() -> {
				if (next.exception != null) {
					out.completeExceptionally(next.exception);
				} else {
					out.complete(next.result);
				}
			});
		});
	}

	@Override
	public <U> TCompletableFuture<U> thenComposeAsync(
			Function<? super T, ? extends TCompletionStage<U>> fn, Executor executor) {
		return thenCompose(fn);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <U, V> TCompletableFuture<V> thenCombine(TCompletionStage<? extends U> other,
			BiFunction<? super T, ? super U, ? extends V> fn) {
		TCompletableFuture<? extends U> that = (TCompletableFuture<? extends U>) other;
		return derive((out, err) -> {
			if (err != null) {
				out.completeExceptionally(err);
				return;
			}
			that.onSettled(() -> {
				if (that.exception != null) {
					out.completeExceptionally(that.exception);
				} else {
					out.complete(fn.apply(result, that.result));
				}
			});
		});
	}

	@Override
	public <U> TCompletableFuture<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
		return derive((out, err) -> out.complete(fn.apply(result, err)));
	}

	@Override
	public TCompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
		TCompletableFuture<T> out = new TCompletableFuture<>();
		onSettled(() -> {
			try {
				action.accept(result, exception);
			} catch (Throwable t) {
				out.completeExceptionally(t);
				return;
			}
			if (exception != null) {
				out.completeExceptionally(exception);
			} else {
				out.complete(result);
			}
		});
		return out;
	}

	@Override
	public TCompletableFuture<T> whenCompleteAsync(BiConsumer<? super T, ? super Throwable> action,
			Executor executor) {
		return whenComplete(action);
	}

	@Override
	public TCompletableFuture<T> exceptionally(Function<Throwable, ? extends T> fn) {
		TCompletableFuture<T> out = new TCompletableFuture<>();
		onSettled(() -> {
			try {
				out.complete(exception != null ? fn.apply(exception) : result);
			} catch (Throwable t) {
				out.completeExceptionally(t);
			}
		});
		return out;
	}

	/**
	 * With everything completing inline, "whichever finishes first" is always this one if
	 * it is already done, and the other otherwise.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public TCompletableFuture<T> applyToEither(TCompletionStage<? extends T> other,
			Function<? super T, ? extends T> fn) {
		TCompletableFuture<? extends T> that = (TCompletableFuture<? extends T>) other;
		TCompletableFuture<T> out = new TCompletableFuture<>();
		Runnable pick = () -> {
			if (out.isDone()) {
				return;
			}
			TCompletableFuture<? extends T> winner = done ? this : that;
			if (winner.exception != null) {
				out.completeExceptionally(winner.exception);
			} else {
				out.complete(fn.apply(winner.result));
			}
		};
		onSettled(pick);
		that.onSettled(pick);
		return out;
	}

	public TCompletableFuture<T> toCompletableFuture() {
		return this;
	}
}
