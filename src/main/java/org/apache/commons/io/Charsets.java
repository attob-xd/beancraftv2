package org.apache.commons.io;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * commons-io's class, with one change: it does not call Collections.unmodifiableSortedMap.
 *
 * TeaVM's Collections has unmodifiableList, unmodifiableCollection, unmodifiableSet and
 * unmodifiableMap, and no sorted or navigable variants at all. commons-io builds
 * STANDARD_CHARSET_MAP in a static initialiser and wraps it with the missing one, so merely
 * *touching* this class threw NoSuchMethodError.
 *
 * That is worth spelling out, because the crash lands nowhere near a charset. Vanilla's
 * ShaderInstance reads every shader through IOUtils.toString(InputStream, Charset), which
 * calls Charsets.toCharset, which triggers this class's initialiser - so the client died in
 * GlslPreprocessor.processImports with a charset error while loading shaders.
 *
 * toCharset does not use the map. Nothing in Minecraft calls requiredCharsets() at all. So the
 * map stays, built exactly as commons-io builds it, and the only thing that changes is how it
 * is handed out.
 *
 * One stated divergence: requiredCharsets() returns a fresh copy rather than an unmodifiable
 * view, so a caller that mutated the result would corrupt its own copy instead of getting an
 * UnsupportedOperationException. The guarantee callers actually depend on - that they cannot
 * affect anyone else's copy - is kept. A real unmodifiable SortedMap view would mean writing
 * the wrapper TeaVM is missing, which is not worth it for a method this build never calls.
 */
public class Charsets {

	private static final SortedMap<String, Charset> STANDARD_CHARSET_MAP;

	@Deprecated
	public static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;
	@Deprecated
	public static final Charset US_ASCII = StandardCharsets.US_ASCII;
	@Deprecated
	public static final Charset UTF_16 = StandardCharsets.UTF_16;
	@Deprecated
	public static final Charset UTF_16BE = StandardCharsets.UTF_16BE;
	@Deprecated
	public static final Charset UTF_16LE = StandardCharsets.UTF_16LE;
	@Deprecated
	public static final Charset UTF_8 = StandardCharsets.UTF_8;

	/** See the class comment: a copy, not a view. */
	public static SortedMap<String, Charset> requiredCharsets() {
		return new TreeMap<>(STANDARD_CHARSET_MAP);
	}

	public static Charset toCharset(final Charset charset) {
		return charset == null ? Charset.defaultCharset() : charset;
	}

	public static Charset toCharset(final String charsetName) throws UnsupportedCharsetException {
		return charsetName == null ? Charset.defaultCharset() : Charset.forName(charsetName);
	}

	static {
		final SortedMap<String, Charset> standardCharsetMap =
				new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		standardCharsetMap.put(StandardCharsets.ISO_8859_1.name(), StandardCharsets.ISO_8859_1);
		standardCharsetMap.put(StandardCharsets.US_ASCII.name(), StandardCharsets.US_ASCII);
		standardCharsetMap.put(StandardCharsets.UTF_16.name(), StandardCharsets.UTF_16);
		standardCharsetMap.put(StandardCharsets.UTF_16BE.name(), StandardCharsets.UTF_16BE);
		standardCharsetMap.put(StandardCharsets.UTF_16LE.name(), StandardCharsets.UTF_16LE);
		standardCharsetMap.put(StandardCharsets.UTF_8.name(), StandardCharsets.UTF_8);
		STANDARD_CHARSET_MAP = standardCharsetMap;
	}
}
