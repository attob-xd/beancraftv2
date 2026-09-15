package org.teavm.classlib.java.nio.file;

import java.io.IOException;

/** See TNoSuchFileException for why this does not extend FileSystemException. */
public class TNotDirectoryException extends IOException {

	public TNotDirectoryException(String file) {
		super(file);
	}
}
