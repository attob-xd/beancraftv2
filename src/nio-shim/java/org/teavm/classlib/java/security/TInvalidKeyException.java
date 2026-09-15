package org.teavm.classlib.java.security;

/** Missing from TeaVM's class library. The JCA is not available; see TKey. */
public class TInvalidKeyException extends java.security.GeneralSecurityException {

	public TInvalidKeyException() {
	}

	public TInvalidKeyException(String message) {
		super(message);
	}
}
