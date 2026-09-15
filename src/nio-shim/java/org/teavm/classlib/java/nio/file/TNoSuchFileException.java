package org.teavm.classlib.java.nio.file;

import java.io.IOException;

/**
 * Extends IOException rather than FileSystemException: TeaVM's class library already maps
 * its own TFileSystemException onto java.nio.file.FileSystemException, and defining a
 * second one here would give TeaVM two classes for that name. Vanilla only ever catches
 * this as an IOException, so the shallower hierarchy costs nothing.
 */
public class TNoSuchFileException extends IOException {

	private final String file;

	public TNoSuchFileException(String file) {
		super(file);
		this.file = file;
	}

	public TNoSuchFileException(String file, String other, String reason) {
		super(file + (reason != null ? " -> " + reason : ""));
		this.file = file;
	}

	public String getFile() {
		return file;
	}
}
