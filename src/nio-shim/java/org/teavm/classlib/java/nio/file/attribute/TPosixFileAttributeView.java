package org.teavm.classlib.java.nio.file.attribute;

/**
 * Named only so vanilla's "does this filesystem support POSIX attributes?" check has a
 * class to ask about. It never does - the browser has no file permissions - so this
 * carries no methods.
 */
public interface TPosixFileAttributeView {
}
