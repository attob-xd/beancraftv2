package org.teavm.classlib.java.nio.file;

/** Part of the java.nio.file shim; see TPath. */
public class TProviderMismatchException extends RuntimeException {

	public TProviderMismatchException() {
	}

	public TProviderMismatchException(String msg) {
		super(msg);
	}
}
