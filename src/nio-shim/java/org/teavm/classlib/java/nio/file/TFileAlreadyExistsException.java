package org.teavm.classlib.java.nio.file;

import java.io.IOException;

/** See TNoSuchFileException for why this does not extend FileSystemException. */
public class TFileAlreadyExistsException extends IOException {

	public TFileAlreadyExistsException(String file) {
		super(file);
	}
}
