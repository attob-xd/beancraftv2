package org.apache.commons.compress.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Replaces the one commons-compress helper vanilla calls outside the archive classes. Unlike
 * those, this one does real work - it is a plain stream copy with no archive format involved,
 * so there is no reason for it to refuse.
 */
public final class IOUtils {

	private IOUtils() {
	}

	public static long copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[8192];
		long total = 0L;
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
			total += read;
		}
		return total;
	}
}
