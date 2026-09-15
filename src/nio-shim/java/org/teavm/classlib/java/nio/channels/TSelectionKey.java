package org.teavm.classlib.java.nio.channels;

/** Missing from TeaVM's class library; see TSelector - there is nothing to select on. */
public abstract class TSelectionKey {

	public static final int OP_READ = 1;
	public static final int OP_WRITE = 4;
	public static final int OP_CONNECT = 8;
	public static final int OP_ACCEPT = 16;

	protected TSelectionKey() {
	}

	public abstract boolean isValid();

	public abstract void cancel();

	public abstract int interestOps();

	public abstract int readyOps();
}
