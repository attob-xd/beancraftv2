package org.teavm.classlib.java.util.concurrent;

/**
 * Missing from TeaVM's class library. Minecraft subclasses it - Util$2, the thread the pool
 * factory would hand back - so the type and its (ForkJoinPool) constructor have to exist for
 * that subclass to link, even though nothing ever constructs one: TForkJoinPool runs tasks
 * on the calling thread and never asks its factory for a thread.
 *
 * It extends Thread because Util calls setName on the result, and because that is what it is
 * in the real class library.
 */
public class TForkJoinWorkerThread extends Thread {

	private final TForkJoinPool pool;

	protected TForkJoinWorkerThread(TForkJoinPool pool) {
		this.pool = pool;
	}

	public TForkJoinPool getPool() {
		return pool;
	}

	/** A pool that never starts a thread has no index to give it. */
	public int getPoolIndex() {
		return 0;
	}

	protected void onStart() {
	}

	protected void onTermination(Throwable exception) {
	}

	/**
	 * Unreachable for the reason in the class comment - the factory is never called, so this
	 * thread is never created and never started.
	 */
	@Override
	public void run() {
	}
}
