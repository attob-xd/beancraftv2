package org.teavm.classlib.java.lang;

import net.lax1dude.eaglercraft.internal.PlatformRuntime;

/**
 * Replaces TeaVM's own Runtime, which is otherwise fine but has no availableProcessors().
 *
 * Minecraft's Util sizes its worker pools from it in a static initialiser, so the client
 * died the moment anything touched Util - which is almost immediately, since Heightmap's
 * types are built through Util.make. TeaVM has no partial-class mechanism, so adding one
 * method means supplying the class.
 *
 * The answer is 1, and that is not a placeholder: a page has one thread. Every executor in
 * this build runs its work inline (see org.teavm.classlib.java.util.concurrent.TExecutors),
 * so a pool sized for more would be a pool of nothing. Reporting 1 makes Minecraft's own
 * sizing arithmetic - which is max(1, cpus - 1) in places - come out right.
 *
 * Memory goes through EaglercraftX's PlatformRuntime, which is where its own maxMemory /
 * totalMemory / freeMemory already read from, so the two agree.
 */
public class TRuntime {

	private static final TRuntime instance = new TRuntime();

	public static TRuntime getRuntime() {
		return instance;
	}

	public int availableProcessors() {
		return 1;
	}

	public long maxMemory() {
		return PlatformRuntime.maxMemory();
	}

	public long totalMemory() {
		return PlatformRuntime.totalMemory();
	}

	public long freeMemory() {
		return PlatformRuntime.freeMemory();
	}

	/** A page cannot exit itself, and closing the tab is the user's business. */
	public void exit(int code) {
	}

	public void halt(int code) {
	}

	/** The browser's collector is not ours to run. */
	public void gc() {
	}

	public void addShutdownHook(Thread hook) {
	}

	public boolean removeShutdownHook(Thread hook) {
		return false;
	}
}
