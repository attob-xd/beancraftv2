package org.teavm.classlib.java.security;

/** Missing from TeaVM's class library. The JCA is not available; see TKey. */
public class TSignatureException extends java.security.GeneralSecurityException {

	public TSignatureException() {
	}

	public TSignatureException(String message) {
		super(message);
	}
}
