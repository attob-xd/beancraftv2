package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/**
 * TeaVM's class library has no java.util.concurrent.locks at all. Minecraft locks in a
 * dozen places - the chunk map, the tint cache, the entity data - and guava's caches lock
 * per segment.
 *
 * A page runs on one thread and a web worker runs on its own, with no shared memory
 * between them: two threads never contend for one of these. So a lock here is a counter
 * that records how deep the holder is, which keeps the reentrancy accounting honest, and
 * acquiring one never waits, because there is nobody to wait for.
 */
public interface TLock {

	void lock();

	void lockInterruptibly();

	boolean tryLock();

	boolean tryLock(long time, TimeUnit unit);

	void unlock();
}
