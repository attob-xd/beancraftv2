package org.teavm.classlib.java.security;

/** Missing from TeaVM's class library; the base of the JCA's exceptions. See TKey. */
public class TGeneralSecurityException extends Exception {

	public TGeneralSecurityException() {
	}

	public TGeneralSecurityException(String message) {
		super(message);
	}

	public TGeneralSecurityException(String message, Throwable cause) {
		super(message, cause);
	}
}
