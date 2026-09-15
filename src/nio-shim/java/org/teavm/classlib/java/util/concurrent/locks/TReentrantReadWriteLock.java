package org.teavm.classlib.java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * See TLock. Readers and writers never contend here, so both sides are the same lock.
 *
 * <p>The accessors return the JDK's nested {@code ReadLock} and {@code WriteLock} types rather
 * than plain {@code Lock}, and that is not cosmetic. {@code ReadWriteLock} declares
 * {@code Lock writeLock()}, but {@code ReentrantReadWriteLock} narrows the return type, so
 * javac emits the call against the narrower descriptor:
 *
 * <pre>    writeLock()Ljava/util/concurrent/locks/ReentrantReadWriteLock$WriteLock;</pre>
 *
 * <p>A shim declaring only the {@code Lock}-returning version does not provide that method, and
 * the call fails at run time with
 * {@code NoSuchMethodError: Method not found: ...writeLock()...$WriteLock;}.
 *
 * <p>That one missing method broke every multiplayer join. {@code BlockTintCache} holds a
 * {@code ReentrantReadWriteLock}, {@code ClientLevel.clearTintCaches} takes its write lock, and
 * {@code LevelRenderer.allChanged} calls that while {@code ClientboundLoginPacket} is being
 * handled - so the packet that builds the level threw, was logged and skipped, and every packet
 * after it hit a null level or a null player. The visible symptom was a connection that
 * completed its handshake and then sat there until "Handshake timed out".
 *
 * <p>The two independent locks are deliberate and unchanged from the previous shim: TeaVM's
 * green threads only switch at yield points, and neither lock() nor unlock() is one, so a
 * reader and a writer cannot actually interleave. Sharing a single lock would be stricter than
 * necessary and would make two readers exclude each other, which this cache does not expect.
 */
public class TReentrantReadWriteLock implements ReadWriteLock {

	/** Delegates to a ReentrantLock; exists so the nested type names match the JDK's. */
	public static class ReadLock implements Lock {

		private final Lock delegate = new ReentrantLock();

		ReadLock() {
		}

		@Override
		public void lock() {
			delegate.lock();
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			delegate.lockInterruptibly();
		}

		@Override
		public boolean tryLock() {
			return delegate.tryLock();
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			return delegate.tryLock(time, unit);
		}

		@Override
		public void unlock() {
			delegate.unlock();
		}

		@Override
		public Condition newCondition() {
			return delegate.newCondition();
		}
	}

	/** See {@link ReadLock}. */
	public static class WriteLock implements Lock {

		private final Lock delegate = new ReentrantLock();

		WriteLock() {
		}

		@Override
		public void lock() {
			delegate.lock();
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			delegate.lockInterruptibly();
		}

		@Override
		public boolean tryLock() {
			return delegate.tryLock();
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			return delegate.tryLock(time, unit);
		}

		@Override
		public void unlock() {
			delegate.unlock();
		}

		@Override
		public Condition newCondition() {
			return delegate.newCondition();
		}
	}

	private final ReadLock read = new ReadLock();
	private final WriteLock write = new WriteLock();

	public TReentrantReadWriteLock() {
	}

	public TReentrantReadWriteLock(boolean fair) {
	}

	@Override
	public ReadLock readLock() {
		return read;
	}

	@Override
	public WriteLock writeLock() {
		return write;
	}

	public final boolean isFair() {
		return false;
	}

	public int getReadLockCount() {
		return 0;
	}

	public boolean isWriteLocked() {
		return false;
	}

	public boolean isWriteLockedByCurrentThread() {
		return false;
	}

	public int getWriteHoldCount() {
		return 0;
	}

	public int getReadHoldCount() {
		return 0;
	}
}
