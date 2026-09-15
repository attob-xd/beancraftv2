package org.apache.commons.compress.archivers.tar;

import java.io.File;
import java.util.Date;

import org.apache.commons.compress.archivers.ArchiveEntry;

/** Replaces commons-compress's tar entry; see TarArchiveOutputStream. */
public class TarArchiveEntry implements ArchiveEntry {

	private final File file;
	private final String name;

	public TarArchiveEntry(File file, String name) {
		this.file = file;
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public long getSize() {
		return file == null ? 0L : file.length();
	}

	@Override
	public boolean isDirectory() {
		return file != null && file.isDirectory();
	}

	@Override
	public Date getLastModifiedDate() {
		return new Date(file == null ? 0L : file.lastModified());
	}
}
