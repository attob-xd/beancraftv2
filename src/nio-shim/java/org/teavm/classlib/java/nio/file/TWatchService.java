package org.teavm.classlib.java.nio.file;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

/**
 * A watch service that never fires. The only caller is PackSelectionScreen$Watcher, which
 * re-scans the resource-pack folder when the user edits it from a file manager - there is
 * no file manager behind a browser tab, and packs get added through EaglercraftX's own UI,
 * which refreshes the list itself.
 */
public class TWatchService implements Closeable {

	public TWatchKey register(TPath path) {
		return () -> path;
	}

	public TWatchKey poll() {
		return null;
	}

	public TWatchKey poll(long timeout, TimeUnit unit) {
		return null;
	}

	public TWatchKey take() {
		return null;
	}

	@Override
	public void close() {
	}
}
