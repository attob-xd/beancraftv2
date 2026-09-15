package org.teavm.classlib.java.security;

/** Missing from TeaVM's class library. The JCA is not available; see TKey. */
public class TNoSuchAlgorithmException extends java.security.GeneralSecurityException {

	public TNoSuchAlgorithmException() {
	}

	public TNoSuchAlgorithmException(String message) {
		super(message);
	}
}
