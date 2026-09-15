package org.teavm.classlib.java.nio.file;

/** Part of the java.nio.file shim; see TPath. */
public class TReadOnlyFileSystemException extends RuntimeException {

	public TReadOnlyFileSystemException() {
	}

	public TReadOnlyFileSystemException(String msg) {
		super(msg);
	}
}
