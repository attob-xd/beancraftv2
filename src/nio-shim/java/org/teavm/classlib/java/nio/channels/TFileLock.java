package org.teavm.classlib.java.nio.channels;

/**
 * Missing from TeaVM's class library. A file lock keeps two processes off one save; a page
 * has no second process to keep out, and DirectoryLock is the only caller.
 */
public abstract class TFileLock implements AutoCloseable {

	protected TFileLock() {
	}

	public boolean isValid() {
		return true;
	}

	public void release() {
	}

	@Override
	public void close() {
	}
}
