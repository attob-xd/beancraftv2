package org.teavm.classlib.java.nio.file;

import java.io.IOException;
import org.teavm.classlib.java.nio.file.attribute.TBasicFileAttributes;

public class TSimpleFileVisitor<T> implements TFileVisitor<T> {

	protected TSimpleFileVisitor() {
	}

	@Override
	public TFileVisitResult preVisitDirectory(T dir, TBasicFileAttributes attrs) throws IOException {
		return TFileVisitResult.CONTINUE;
	}

	@Override
	public TFileVisitResult visitFile(T file, TBasicFileAttributes attrs) throws IOException {
		return TFileVisitResult.CONTINUE;
	}

	@Override
	public TFileVisitResult visitFileFailed(T file, IOException exc) throws IOException {
		throw exc;
	}

	@Override
	public TFileVisitResult postVisitDirectory(T dir, IOException exc) throws IOException {
		if (exc != null) {
			throw exc;
		}
		return TFileVisitResult.CONTINUE;
	}
}
