package org.apache.commons.compress.archivers.tar;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;

/**
 * Replaces commons-compress's tar writer.
 *
 * The type exists for a load-time reason rather than a runtime one. Realms' upload screen
 * declares addFileToTarGz(TarArchiveOutputStream, ...), TeaVM writes that parameter type into
 * class metadata, and the browser resolves metadata when the script loads - so a missing
 * commons-compress made the page fail to start, not merely fail if someone opened Realms.
 * commons-compress itself is desktop-only and deliberately not on TeaVM's classpath.
 *
 * Writing is refused rather than faked. The only caller packs a world for upload to Realms,
 * which this client cannot reach in the first place (see FileUpload), so a half-written
 * archive would be worse than a clear refusal.
 */
public class TarArchiveOutputStream extends OutputStream {

	public static final int LONGFILE_ERROR = 0;
	public static final int LONGFILE_TRUNCATE = 1;
	public static final int LONGFILE_GNU = 2;
	public static final int LONGFILE_POSIX = 3;

	private final OutputStream out;

	public TarArchiveOutputStream(OutputStream out) {
		this.out = out;
	}

	private static IOException nope() {
		return new IOException("This build cannot write tar archives; commons-compress is not"
				+ " available in a browser");
	}

	public void setLongFileMode(int mode) {
	}

	public void putArchiveEntry(ArchiveEntry entry) throws IOException {
		throw nope();
	}

	public void closeArchiveEntry() throws IOException {
		throw nope();
	}

	public void finish() throws IOException {
		throw nope();
	}

	@Override
	public void write(int b) throws IOException {
		throw nope();
	}

	@Override
	public void close() throws IOException {
		if (out != null) {
			out.close();
		}
	}
}
