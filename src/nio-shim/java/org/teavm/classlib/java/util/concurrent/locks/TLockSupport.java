package org.teavm.classlib.java.util.concurrent.locks;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * See TLock. Parking would stop the only thread there is, with nothing left able to unpark it,
 * so park() returns immediately - the caller's loop re-checks its condition and makes progress,
 * which is the behaviour a spurious wakeup already allows for.
 *
 * That reasoning holds only while the condition can eventually become true. When it cannot,
 * this turns the caller into a tight infinite spin, and the symptom is brutally unhelpful: the
 * page pins one core, allocates almost nothing, logs nothing and never paints another frame -
 * so there is no exception, no stack, and no way in from the outside, because screenshots and
 * javascript both time out against a busy renderer.
 *
 * Minecraft is full of such loops. BlockableEventLoop.managedBlock is
 *
 *     while (!isDone.getAsBoolean()) { if (!pollTask()) waitForTasks(); }
 *
 * and waitForTasks() is Thread.yield() followed by LockSupport.parkNanos. Both were no-ops
 * here, and that was the bug rather than a shortcut.
 *
 * TeaVM's threads are real but cooperative: Thread.start() gives you a green thread, and it
 * keeps the CPU until it reaches something that yields. A park that returns immediately is not
 * one, so a loop built on park does not wait - it burns. MinecraftServer spins in exactly this
 * shape on purpose: every tick sets delayedTasksMaxNextTickTime 50ms ahead and calls
 * waitUntilNextTick, whose managedBlock loops until that time passes. On a JVM the park sleeps
 * and the OS runs the other threads. Here it ran the loop at about 1.4 million parks a second
 * for the whole 50ms, every tick, and the client thread got the scraps: a singleplayer world
 * sat at 15% loaded indefinitely while the server ticked along beside it perfectly happily.
 *
 * So park sleeps a moment instead - see yieldSlice. On TeaVM Thread.sleep is a continuation
 * that returns to the event loop, which is what actually lets the other green threads and the
 * renderer run; Thread.yield() alone is not enough, because it only switches after the thread
 * has held the CPU for 100ms. Sleeping briefly and returning is a spurious wakeup, which every
 * caller of park already tolerates, so nothing has to change around it.
 *
 * The park counter stays, but as a rate meter rather than an alarm: a server idling between
 * ticks parks enormously often by design, so only the *timing* in the report separates that
 * from a loop that cannot finish. Reading the count alone sent this session chasing a false
 * positive for an hour. See SPIN_THRESHOLD.
 *
 * The threshold is small now because a park is no longer free: it sleeps at least a
 * millisecond, so the old 200,000 would have taken over three minutes to reach and the report
 * would never have arrived while anyone was watching. A few thousand parks with the elapsed
 * time beside them says everything the old count did, sooner.
 */
public final class TLockSupport {

	/**
	 * Parks per report window.
	 *
	 * <p>The count alone does not mean anything, which the first version of this got wrong: it
	 * never reset the counter, so the 200,000th park of the <i>session</i> tripped it, and
	 * Minecraft reaches that in seconds of ordinary idling - {@code waitUntilNextTick} spins in
	 * exactly this way between every pair of server ticks, on purpose. The warning it printed
	 * during world loading was very likely benign, and it sent this debugging session after the
	 * wrong thing.
	 *
	 * <p>So the counter resets each window and the report carries how long the window took.
	 * That is the number that separates the two cases: 200,000 parks spread over seconds is a
	 * loop idling between ticks, while 200,000 in a few tens of milliseconds, repeating, is a
	 * loop that cannot finish. Read the elapsed time, not the count.
	 */
	private static final int SPIN_THRESHOLD = 2000;

	/**
	 * How many times the report may fire. One report names the first loop to spin, which is
	 * not necessarily the only one - and if a second loop is spinning for a different reason,
	 * knowing that costs another 25-minute build to find out otherwise.
	 */
	private static final int MAX_REPORTS = 4;

	private static int parkCount;
	private static int reports;
	private static long windowStart;

	private TLockSupport() {
	}

	private static void park0() {
		if (++parkCount <= SPIN_THRESHOLD) {
			return;
		}

		long now = System.currentTimeMillis();
		long elapsed = windowStart == 0L ? -1L : now - windowStart;
		parkCount = 0;
		windowStart = now;

		if (reports >= MAX_REPORTS) {
			return;
		}
		++reports;

		String who;
		try {
			who = Thread.currentThread().getName();
		} catch (Throwable t) {
			who = "<unknown>";
		}

		System.err.println("LockSupport: " + SPIN_THRESHOLD + " parks on thread \"" + who
				+ "\" in " + (elapsed < 0L ? "?" : Long.toString(elapsed)) + " ms"
				+ " (report " + reports + " of " + MAX_REPORTS + "). Stack:");
		System.err.println(spinStack());
	}

	/**
	 * The stack of the loop that is spinning.
	 *
	 * <p>The throwable is thrown and caught rather than merely constructed, which looks
	 * pointless and is the entire reason this works. TeaVM fills a stack in when a throwable is
	 * <i>thrown</i>, not when it is created: the first version of this printed
	 * {@code EagRuntime.getStackTrace(new Throwable(...))} and got back the single line
	 * "at [no stack trace]", so the one fact the detector exists to report was the one thing it
	 * did not say, and the spin had to be chased by reasoning instead.
	 */
	private static String spinStack() {
		try {
			throw new Throwable("spin detected");
		} catch (Throwable thrown) {
			try {
				return EagRuntime.getStackTrace(thrown);
			} catch (Throwable t) {
				return "  (could not read the stack: " + t + ")";
			}
		}
	}

	/**
	 * Hands the rest of this thread's slice back, which is the whole job.
	 *
	 * <p>Returning immediately - what this did - is not merely a missed optimisation on TeaVM,
	 * because TeaVM's threads are cooperative: a thread keeps running until it reaches a point
	 * that yields, and a park that returns at once is not one. {@code MinecraftServer} spins
	 * deliberately here. Every tick it sets {@code delayedTasksMaxNextTickTime} 50ms ahead and
	 * calls {@code waitUntilNextTick}, whose {@code managedBlock} loops until that time passes:
	 * on a JVM the park sleeps and the OS runs other threads, and here it burned the entire
	 * remainder of all 50ms at about 1.4 million parks a second. The client thread got what was
	 * left, which was nothing much, and a world sat at 15% loaded indefinitely while the server
	 * ticked happily beside it.
	 *
	 * <p>{@code Thread.sleep} is the yield to use because TeaVM implements it as a real
	 * continuation - it returns to the event loop, which is what lets both the other green
	 * threads and the browser's own rendering run. {@code Thread.yield()} is not enough on its
	 * own: it only switches once the thread has held the CPU for 100ms, so a loop calling it
	 * still burns 100ms at a time, and {@code waitForTasks} already calls it.
	 *
	 * <p>The sleep is at least 1ms, which browsers clamp upwards anyway, and that is fine: this
	 * is a wait, and overshooting only means the enclosing loop re-tests its condition a few
	 * times rather than tens of thousands. It is capped so an untimed park cannot stall a
	 * thread that a spurious wakeup would have freed - which the {@code park()} contract
	 * explicitly permits, and every caller here re-checks its condition in a loop.
	 */
	private static void yieldSlice(long nanos) {
		park0();

		// Free-spin for a short budget, then yield once. Neither extreme works here.
		long now = System.currentTimeMillis();
		if (spinWindowStart == 0L) {
			spinWindowStart = now;
			return;
		}
		if (now - spinWindowStart < SPIN_BUDGET_MILLIS) {
			return;
		}
		spinWindowStart = 0L;

		long millis = nanos <= 0L ? 1L : nanos / 1000000L;
		if (millis < 1L) {
			millis = 1L;
		} else if (millis > MAX_PARK_MILLIS) {
			millis = MAX_PARK_MILLIS;
		}
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// park() returns silently when interrupted and leaves the flag for the caller,
			// which is what java.util.concurrent.locks.LockSupport documents.
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * How long a thread may spin through parks before it owes the others a yield.
	 *
	 * <p>Both extremes were tried and both are wrong, for opposite reasons. Returning from park
	 * immediately - the original - let MinecraftServer burn every 50ms tick at about 1.4 million
	 * parks a second, and the client thread, which is the one that renders, got nothing: a world
	 * sat at 15% loaded for ever. Sleeping on every park fixed that and broke the other side,
	 * because the busy loop had been doing real work. {@code managedBlock} is
	 * {@code while (!done) { if (!pollTask()) waitForTasks(); }}, so a park is what happens when
	 * a poll finds nothing, and polling again immediately is how chunk work gets picked up the
	 * moment it appears. Browsers clamp a 1ms sleep to about 4ms, so paying that on every miss
	 * throttled world generation roughly a hundredfold - spawn preparation went from 53 seconds
	 * to 1% in four minutes.
	 *
	 * <p>So: poll freely for this long, then sleep once. The spin keeps the fast path fast, and
	 * the sleep is a real continuation back to the event loop, which is the only thing that lets
	 * the other green thread and the renderer run at all.
	 */
	private static final long SPIN_BUDGET_MILLIS = 8L;

	private static long spinWindowStart;

	/**
	 * Longest a single park may sleep. An untimed {@code park()} means "until unparked", and
	 * nothing here can unpark, so it has to come back and let its caller re-test.
	 */
	private static final long MAX_PARK_MILLIS = 50L;

	public static void park() {
		yieldSlice(0L);
	}

	public static void park(Object blocker) {
		yieldSlice(0L);
	}

	public static void parkNanos(long nanos) {
		yieldSlice(nanos);
	}

	public static void parkNanos(Object blocker, long nanos) {
		yieldSlice(nanos);
	}

	public static void parkUntil(long deadline) {
		yieldSlice(0L);
	}

	public static void parkUntil(Object blocker, long deadline) {
		yieldSlice(0L);
	}

	public static void unpark(Thread thread) {
	}

	public static Object getBlocker(Thread thread) {
		return null;
	}
}
