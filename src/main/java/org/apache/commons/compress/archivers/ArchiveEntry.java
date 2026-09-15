package org.apache.commons.compress.archivers;

import java.util.Date;

/**
 * Replaces commons-compress's archive entry.
 *
 * commons-compress is not on TeaVM's classpath - it is a desktop-only dependency - but
 * Realms' upload screen names TarArchiveOutputStream as a method parameter, and TeaVM writes
 * parameter types into class metadata the browser evaluates when the script loads. An
 * unresolvable class there is a ReferenceError before any game code runs, so the type has to
 * exist even though nothing in a browser can write a tar archive. See TarArchiveOutputStream.
 */
public interface ArchiveEntry {

	String getName();

	long getSize();

	boolean isDirectory();

	Date getLastModifiedDate();
}
