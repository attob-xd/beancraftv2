package org.teavm.classlib.java.util.concurrent;

/** Missing from TeaVM's class library; thrown by an executor that has been shut down. */
public class TRejectedExecutionException extends RuntimeException {

	public TRejectedExecutionException() {
	}

	public TRejectedExecutionException(String message) {
		super(message);
	}

	public TRejectedExecutionException(String message, Throwable cause) {
		super(message, cause);
	}

	public TRejectedExecutionException(Throwable cause) {
		super(cause);
	}
}
