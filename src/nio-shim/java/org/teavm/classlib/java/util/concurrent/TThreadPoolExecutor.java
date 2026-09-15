package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Missing from TeaVM's class library, and it has to be usable, not merely present.
 *
 * <p>An earlier version of this class threw from its constructor, on the reasoning that a page
 * has one thread and so cannot have a thread pool. True, and still the wrong thing to do:
 * vanilla holds its pools in <i>static fields</i>, so refusing to construct one does not
 * decline the pool, it fails the class initialiser of whatever holds it.
 * {@code ServerSelectionList.THREAD_POOL} is exactly that - a static
 * {@code new ScheduledThreadPoolExecutor(5, ...)} - so opening the Multiplayer screen died on
 * it before the server list could be drawn.
 *
 * <p>So it runs the work instead, inline on the calling thread, which is what every other
 * executor in this build does; see {@link TExecutors} for why that is the only meaning "async"
 * can have here. The pool-shaped accessors answer honestly about a pool of one.
 *
 * <p>The cost is the same one {@link TExecutors} carries and is worth restating: work the
 * caller expected to happen off the main thread happens on it, so the page blocks for as long
 * as the task takes. For the server pinger that owns this pool, that means the list fills in
 * between frames rather than in the background.
 */
public class TThreadPoolExecutor extends TAbstractExecutorService {

	private final int corePoolSize;
	private volatile boolean shutdown;
	private long completedTasks;

	public TThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
			TimeUnit unit, BlockingQueue<Runnable> workQueue) {
		this.corePoolSize = corePoolSize;
	}

	public TThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
			TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
	}

	/** For subclasses that size themselves, notably {@link TScheduledThreadPoolExecutor}. */
	protected TThreadPoolExecutor(int corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	@Override
	public void execute(Runnable command) {
		if (shutdown) {
			throw new RejectedExecutionException("executor has been shut down");
		}
		if (command != null) {
			command.run();
			++completedTasks;
		}
	}

	@Override
	public void shutdown() {
		shutdown = true;
	}

	@Override
	public List<Runnable> shutdownNow() {
		shutdown = true;
		// Nothing can be pending: execute() runs the task before it returns.
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
		// Every task finished before the call that submitted it returned.
		return true;
	}

	// ---- pool-shaped accessors, answering for a pool of one ----

	public int getCorePoolSize() {
		return corePoolSize;
	}

	public int getMaximumPoolSize() {
		return 1;
	}

	public int getPoolSize() {
		return 1;
	}

	public int getActiveCount() {
		return 0;
	}

	public long getTaskCount() {
		return completedTasks;
	}

	public long getCompletedTaskCount() {
		return completedTasks;
	}

	public int getQueueLength() {
		return 0;
	}

	public void setCorePoolSize(int size) {
	}

	public void setMaximumPoolSize(int size) {
	}

	public void allowCoreThreadTimeOut(boolean value) {
	}

	public boolean allowsCoreThreadTimeOut() {
		return false;
	}
}
