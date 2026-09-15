package org.teavm.classlib.java.nio.channels;

/**
 * Missing from TeaVM's class library. A selector multiplexes sockets, and a page has none;
 * the type is named by the dead server networking code. See TSocket.
 */
public abstract class TSelector implements AutoCloseable {

	protected TSelector() {
	}

	public static TSelector open() {
		throw new UnsupportedOperationException("A browser tab has no sockets to select on");
	}

	public abstract boolean isOpen();

	@Override
	public void close() {
	}
}
