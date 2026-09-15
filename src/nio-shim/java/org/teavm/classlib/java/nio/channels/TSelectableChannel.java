package org.teavm.classlib.java.nio.channels;

/** Missing from TeaVM's class library; see TSelector. */
public abstract class TSelectableChannel {

	protected TSelectableChannel() {
	}

	public abstract boolean isOpen();

	public abstract void close();
}
