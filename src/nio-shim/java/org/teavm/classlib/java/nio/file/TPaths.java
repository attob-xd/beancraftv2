package org.teavm.classlib.java.nio.file;

import java.net.URI;

/** See TPath for why this package exists. */
public final class TPaths {

	private TPaths() {
	}

	public static TPath get(String first, String... more) {
		StringBuilder sb = new StringBuilder(first);
		for (String m : more) {
			if (!m.isEmpty()) {
				sb.append('/').append(m);
			}
		}
		return new TEaglerPath(TEaglerPath.normalizeString(sb.toString()));
	}

	public static TPath get(URI uri) {
		if (!"file".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("Only file: URIs are supported here: " + uri);
		}
		return get(uri.getPath());
	}
}
