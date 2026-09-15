package org.teavm.classlib.java.nio.file;

import java.io.File;
import java.net.URI;
import java.util.Iterator;

/**
 * TeaVM's class library has no java.nio.file at all - not in 0.9.2, and not in 0.12
 * either. 1.18.2 moved its whole save and resource-pack layer onto TPath
 * (LevelStorageSource, RegionFileStorage, FileUtil, PackRepository), so the browser build
 * needs one. This package supplies exactly the 76 members the vanilla jar actually
 * invokes; tools/scan_nio_members.py is how that set was measured.
 *
 * TPath is an interface here because it is one in java.base: javac compiles this project
 * against the real JDK (a java.* package cannot be on the ordinary classpath), so every
 * call site is an invokeinterface, and only TeaVM ever sees these classes. The shape has
 * to match or the emitted calls will not bind.
 *
 * Everything is backed by java.io.File, which TeaVM does implement, so TPath and File stay
 * consistent with each other and toFile() is free. TPaths are held as normalised
 * '/'-separated strings.
 *
 * Not supported, because the browser has nothing to map them onto: symbolic links (every
 * TLinkOption is ignored), file permissions, and the watch service (see TWatchService).
 */
public interface TPath extends Comparable<TPath>, Iterable<TPath>, TWatchable {

	TPath resolve(String other);

	TPath resolve(TPath other);

	TPath resolveSibling(String other);

	TPath resolveSibling(TPath other);

	TPath getParent();

	TPath getFileName();

	TPath normalize();

	TPath toAbsolutePath();

	TPath toRealPath(TLinkOption... options);

	boolean isAbsolute();

	TPath relativize(TPath other);

	boolean startsWith(TPath other);

	boolean startsWith(String other);

	boolean endsWith(TPath other);

	boolean endsWith(String other);

	int getNameCount();

	TPath getName(int index);

	TPath subpath(int begin, int end);

	TPath getRoot();

	TFileSystem getFileSystem();

	File toFile();

	URI toUri();

	@Override
	Iterator<TPath> iterator();

	static TPath of(String first, String... more) {
		return TPaths.get(first, more);
	}

	static TPath of(URI uri) {
		return TPaths.get(uri);
	}
}
