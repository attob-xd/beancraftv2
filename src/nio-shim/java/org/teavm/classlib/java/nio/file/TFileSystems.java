package org.teavm.classlib.java.nio.file;

import java.net.URI;
import java.util.Map;

public final class TFileSystems {

	private TFileSystems() {
	}

	public static TFileSystem getDefault() {
		return TFileSystem.DEFAULT;
	}

	/**
	 * Vanilla calls this once, to mount a zipped resource pack through the jar: filesystem
	 * provider. TeaVM ships no such provider, and EaglercraftX reads zipped packs through
	 * its own EPK/zip reader instead, so this path is never the one taken in the browser.
	 */
	public static TFileSystem newFileSystem(URI uri, Map<String, ?> env) {
		throw new TFileSystemNotFoundException(
				"EaglercraftX mounts no filesystems; zipped packs are read directly: " + uri);
	}

	public static TFileSystem newFileSystem(TPath path, ClassLoader loader) {
		throw new TFileSystemNotFoundException(
				"EaglercraftX mounts no filesystems; zipped packs are read directly: " + path);
	}
}
