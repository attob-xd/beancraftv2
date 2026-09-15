package org.teavm.classlib.java.util.concurrent;

/** Missing from TeaVM's class library; nothing here can time out, but the type is named. */
public class TTimeoutException extends Exception {

	public TTimeoutException() {
	}

	public TTimeoutException(String message) {
		super(message);
	}
}
