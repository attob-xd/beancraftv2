package org.teavm.classlib.java.util.concurrent;

/**
 * Missing from TeaVM's class library, and needed by TCompletableFuture - cancel() has to
 * complete the future with this, and callers catch it.
 */
public class TCancellationException extends IllegalStateException {

	public TCancellationException() {
	}

	public TCancellationException(String message) {
		super(message);
	}
}
