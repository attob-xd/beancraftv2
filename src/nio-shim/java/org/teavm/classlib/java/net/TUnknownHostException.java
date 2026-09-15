package org.teavm.classlib.java.net;

/** Missing from TeaVM's class library. A page has no resolver - see TInetAddress. */
public class TUnknownHostException extends java.io.IOException {

	public TUnknownHostException() {
	}

	public TUnknownHostException(String message) {
		super(message);
	}
}
