package org.teavm.classlib.java.nio.file;

import java.util.Collections;
import java.util.List;

/** See TWatchService: nothing here ever reports a change. */
public interface TWatchKey {

	default boolean isValid() {
		return true;
	}

	default List<TWatchEvent<?>> pollEvents() {
		return Collections.emptyList();
	}

	default boolean reset() {
		return true;
	}

	default void cancel() {
	}

	TWatchable watchable();
}
