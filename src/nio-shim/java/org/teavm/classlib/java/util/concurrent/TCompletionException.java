package org.teavm.classlib.java.util.concurrent;

/** Missing from TeaVM's class library; see TCompletableFuture, which throws it from join(). */
public class TCompletionException extends RuntimeException {

	protected TCompletionException() {
	}

	protected TCompletionException(String message) {
		super(message);
	}

	public TCompletionException(String message, Throwable cause) {
		super(message, cause);
	}

	public TCompletionException(Throwable cause) {
		super(cause);
	}
}
