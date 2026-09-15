package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library. Minecraft's Util.makeExecutor builds one of these for
 * every background executor it owns - backgroundExecutor(), ioPool(), bootstrapExecutor() -
 * and it is not optional: the pool size is Mth.clamp(availableProcessors() - 1, 1, 255), and
 * clamp's lower bound is 1, so even the one-processor answer this build gives (see TRuntime)
 * still lands in the branch that says "new ForkJoinPool". The earlier version of this class
 * threw from its constructor, which meant the client died in Util's static initialiser.
 *
 * So it has to be a working executor, and on one thread the only working executor is the one
 * that runs the task inline - the same answer TExecutors gives, for the same reason, and the
 * same one EaglercraftX already relies on when it hands Runnable::run to WorldStem.load.
 *
 * The consequence is worth naming plainly: work Minecraft wrote to happen off the main
 * thread - chunk generation, resource reloading, texture stitching - happens on it, so the
 * page freezes for the duration instead of staying responsive. That is a real cost, not a
 * hidden one, and it is the cost of having one thread rather than a defect in this class.
 *
 * The parallelism, the thread factory and the asyncMode flag are all accepted and kept so
 * getParallelism() can answer honestly, but nothing here ever calls the factory: a thread is
 * never created, so ForkJoinWorkerThread is never instantiated.
 */
public class TForkJoinPool extends TAbstractExecutorService {

	/** Named as a parameter type by Minecraft's Util and by guava; see the class comment. */
	public interface ForkJoinWorkerThreadFactory {

		TForkJoinWorkerThread newThread(TForkJoinPool pool);
	}

	private static TForkJoinPool common;

	private final int parallelism;
	private final ForkJoinWorkerThreadFactory factory;
	private final Thread.UncaughtExceptionHandler handler;
	private final boolean asyncMode;
	private boolean shutdown;

	public TForkJoinPool() {
		this(1);
	}

	public TForkJoinPool(int parallelism) {
		this(parallelism, null, null, false);
	}

	public TForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory,
			Thread.UncaughtExceptionHandler handler, boolean asyncMode) {
		this.parallelism = parallelism;
		this.factory = factory;
		this.handler = handler;
		this.asyncMode = asyncMode;
	}

	/**
	 * The common pool is a real pool here rather than a special case, because there is
	 * nothing special about it when every pool runs inline.
	 */
	public static TForkJoinPool commonPool() {
		if (common == null) {
			common = new TForkJoinPool(1);
		}
		return common;
	}

	public static int getCommonPoolParallelism() {
		return 1;
	}

	public int getParallelism() {
		return parallelism;
	}

	public ForkJoinWorkerThreadFactory getFactory() {
		return factory;
	}

	public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
		return handler;
	}

	public boolean getAsyncMode() {
		return asyncMode;
	}

	/**
	 * An exception escaping the task is handed to the pool's handler if it has one, which is
	 * where Minecraft expects to see it: Util passes Util::onThreadException, and that is
	 * what turns a background failure into a logged crash report rather than silence. With
	 * no handler the exception propagates to the caller, which is the closest thing to a
	 * real pool's "the worker thread dies" that a single thread can offer.
	 */
	@Override
	public void execute(Runnable command) {
		if (shutdown) {
			throw new RejectedExecutionException("shut down");
		}
		if (handler == null) {
			command.run();
			return;
		}
		try {
			command.run();
		} catch (Throwable t) {
			handler.uncaughtException(Thread.currentThread(), t);
		}
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

	/** Every task submitted has already run, so there is never anything to wait for. */
	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) {
		return true;
	}

	public boolean isQuiescent() {
		return true;
	}

	public boolean awaitQuiescence(long timeout, TimeUnit unit) {
		return true;
	}

	public int getPoolSize() {
		return 0;
	}

	public int getRunningThreadCount() {
		return 0;
	}

	public int getActiveThreadCount() {
		return 0;
	}

	public long getQueuedTaskCount() {
		return 0L;
	}

	public int getQueuedSubmissionCount() {
		return 0;
	}

	public boolean hasQueuedSubmissions() {
		return false;
	}
}
