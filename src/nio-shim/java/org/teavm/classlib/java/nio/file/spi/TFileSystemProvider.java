package org.teavm.classlib.java.nio.file.spi;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.teavm.classlib.java.nio.file.TFileSystem;
import org.teavm.classlib.java.nio.file.TPath;

/**
 * Enough of the SPI for vanilla's scan of installedProviders(), which is not the optional
 * lookup it looks like.
 *
 * Util's static initialiser searches the installed providers for one whose scheme is "jar"
 * and calls orElseThrow if it finds none - so a file-only list is not a graceful "no zip
 * support", it is "No jar file system provider found" thrown out of Util's clinit, which is
 * the first vanilla class the client touches. The previous version of this file returned
 * only the file provider on the assumption that the caller would fall back. It does not, and
 * the client died there.
 *
 * So the jar provider exists and is found. What it will not do is mount anything: opening a
 * zip as a filesystem needs random access into a file, and this build's files live in
 * EaglercraftX's virtual filesystem in browser storage. Its one real user is FileZipper,
 * which writes debug dumps and world exports, so the failure lands on a path startup does
 * not take - and lands loudly, naming the path, rather than handing back an empty filesystem
 * that would read as an empty zip.
 */
public abstract class TFileSystemProvider {

	public static final TFileSystemProvider DEFAULT = new TFileSystemProvider() {
		@Override
		public String getScheme() {
			return "file";
		}
	};

	/** Present so vanilla's scan succeeds; see the class comment for why it cannot mount. */
	public static final TFileSystemProvider ZIP = new TFileSystemProvider() {
		@Override
		public String getScheme() {
			return "jar";
		}
	};

	protected TFileSystemProvider() {
	}

	public static List<TFileSystemProvider> installedProviders() {
		return Arrays.asList(DEFAULT, ZIP);
	}

	public abstract String getScheme();

	public TFileSystem newFileSystem(TPath path, Map<String, ?> env) {
		throw new UnsupportedOperationException("Cannot mount " + path
				+ " as a filesystem: this build reads its files through EaglercraftX's"
				+ " virtual filesystem, which has no random access into an archive");
	}
}
