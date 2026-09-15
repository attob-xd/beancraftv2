package org.teavm.classlib.java.nio.file;

public interface TWatchable {

	TWatchKey register(TWatchService watcher, TWatchEvent.Kind<?>... events);
}
