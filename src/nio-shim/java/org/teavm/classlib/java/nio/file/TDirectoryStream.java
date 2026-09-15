package org.teavm.classlib.java.nio.file;

import java.io.Closeable;

public interface TDirectoryStream<T> extends Closeable, Iterable<T> {

	interface Filter<T> {
		boolean accept(T entry) throws java.io.IOException;
	}
}
