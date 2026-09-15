package net.minecraft.client.sounds;

import net.minecraft.util.thread.BlockableEventLoop;

/**
 * Vanilla's sound executor, with its thread removed: tasks run inline on the caller.
 *
 * Vanilla starts a daemon thread whose entire body is
 *
 *     while (!shutdown) { managedBlock(() -> shutdown); }
 *
 * and overrides waitForTasks() to LockSupport.park(). On a desktop that thread sleeps until
 * tell() unparks it, which costs nothing and keeps audio work off the render thread.
 *
 * Here it was fatal. LockSupport.park is a no-op in this build - parking the only thread there
 * is would stop the page with nothing able to wake it, see TLockSupport - so park() returns
 * immediately, `shutdown` is false until flush() sets it, and the loop spins at full speed for
 * ever. Worse, TeaVM threads are cooperative continuations: once this one was scheduled it
 * never yielded back, so the client thread never resumed and Minecraft's constructor never
 * returned. The symptom was one pinned core, no further log line, no frame ever drawn, and no
 * exception to catch - the client simply stopped, a few statements short of a menu.
 *
 * Running inline is the honest adaptation rather than a workaround. A page has one thread, and
 * the audio it schedules is main-thread work anyway: lax1dude's PlatformAudio is Web Audio,
 * which must be driven from the page thread. SoundEngine only ever puts one thing on this
 * executor - updateSource(), which sets the listener's position and orientation - so "run it
 * now" and "run it on the audio thread in a moment" differ by nothing observable.
 *
 * flush() therefore has nothing to drain. Vanilla's version sets shutdown, interrupts the
 * thread, joins it, drops queued tasks and starts a fresh thread; with no thread and no queue,
 * the honest equivalent is to do nothing. Note that its join() would have been a second hang
 * in this environment, for the same reason as the first.
 */
public class SoundEngineExecutor extends BlockableEventLoop<Runnable> {

	public SoundEngineExecutor() {
		super("Sound executor");
	}

	@Override
	protected Runnable wrapRunnable(Runnable task) {
		return task;
	}

	@Override
	protected boolean shouldRun(Runnable task) {
		return true;
	}

	/**
	 * There is no separate sound thread, so the running thread is whichever one is asking.
	 * That also makes isSameThread() true, which is the truthful answer here.
	 */
	@Override
	protected Thread getRunningThread() {
		return Thread.currentThread();
	}

	@Override
	public void execute(Runnable task) {
		task.run();
	}

	@Override
	public void tell(Runnable task) {
		task.run();
	}

	/** Nothing is ever queued, so there is nothing to flush; see the class comment. */
	public void flush() {
	}
}
