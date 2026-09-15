package org.teavm.classlib.java.nio.file;

import java.io.Closeable;
import java.util.Collections;
import java.util.Set;

/**
 * There is exactly one filesystem here - TeaVM's - so this is a thin holder rather than a
 * real provider abstraction. TFileSystems.newFileSystem(URI, Map) is what vanilla uses to
 * mount a resource pack zip as a filesystem; see TFileSystems for why that is refused.
 */
public class TFileSystem implements Closeable {

	static final TFileSystem DEFAULT = new TFileSystem();

	TFileSystem() {
	}

	public TPath getPath(String first, String... more) {
		return TPaths.get(first, more);
	}

	public String getSeparator() {
		return "/";
	}

	public boolean isOpen() {
		return true;
	}

	public boolean isReadOnly() {
		return false;
	}

	public Iterable<TPath> getRootDirectories() {
		return Collections.singletonList(TPaths.get("/"));
	}

	public Set<String> supportedFileAttributeViews() {
		return Collections.singleton("basic");
	}

	public org.teavm.classlib.java.nio.file.spi.TFileSystemProvider provider() {
		return org.teavm.classlib.java.nio.file.spi.TFileSystemProvider.DEFAULT;
	}

	public TWatchService newWatchService() {
		return new TWatchService();
	}

	@Override
	public void close() {
	}
}
