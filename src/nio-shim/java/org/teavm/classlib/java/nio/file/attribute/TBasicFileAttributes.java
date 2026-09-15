package org.teavm.classlib.java.nio.file.attribute;

public interface TBasicFileAttributes {

	TFileTime lastModifiedTime();

	TFileTime lastAccessTime();

	TFileTime creationTime();

	boolean isRegularFile();

	boolean isDirectory();

	boolean isSymbolicLink();

	boolean isOther();

	long size();

	Object fileKey();
}
