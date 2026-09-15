package org.teavm.classlib.java.nio.file;

import java.io.IOException;
import org.teavm.classlib.java.nio.file.attribute.TBasicFileAttributes;

public interface TFileVisitor<T> {

	TFileVisitResult preVisitDirectory(T dir, TBasicFileAttributes attrs) throws IOException;

	TFileVisitResult visitFile(T file, TBasicFileAttributes attrs) throws IOException;

	TFileVisitResult visitFileFailed(T file, IOException exc) throws IOException;

	TFileVisitResult postVisitDirectory(T dir, IOException exc) throws IOException;
}
