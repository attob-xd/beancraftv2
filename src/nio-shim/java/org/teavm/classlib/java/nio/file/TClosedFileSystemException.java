package org.teavm.classlib.java.nio.file;

/** Part of the java.nio.file shim; see TPath. */
public class TClosedFileSystemException extends RuntimeException {

	public TClosedFileSystemException() {
	}

	public TClosedFileSystemException(String msg) {
		super(msg);
	}
}
