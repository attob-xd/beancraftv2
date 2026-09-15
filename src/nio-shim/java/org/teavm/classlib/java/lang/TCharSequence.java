package org.teavm.classlib.java.lang;

import java.util.stream.IntStream;

/**
 * Replaces TeaVM's CharSequence, which is correct as far as it goes but has neither chars()
 * nor codePoints().
 *
 * Both are on the boot path. BitmapProvider.Builder calls String.codePoints() while building
 * the glyph sheets for the default font, which happens during the first resource reload -
 * inside Minecraft's constructor - so the client cannot reach a menu without it. Util calls
 * String.chars().
 *
 * They are added here rather than on String deliberately. In Java these are default methods
 * on CharSequence and String only inherits them, so supplying the interface gets String,
 * StringBuilder and every other implementor at once - and it leaves TeaVM's String, which is
 * threaded through the compiler's own intrinsics, completely alone. Replacing that class to
 * add two methods would be a much larger bet.
 *
 * The abstract methods are exactly TeaVM's, so classes already compiled against the original
 * interface still satisfy this one. (isEmpty() is abstract here, not default as in modern
 * Java, because that is how TeaVM declares it and its implementors define it.)
 */
public interface TCharSequence {

	int length();

	char charAt(int index);

	boolean isEmpty();

	TCharSequence subSequence(int start, int end);

	@Override
	String toString();

	/**
	 * Every char as an int, surrogates included and unpaired - which is what the real
	 * chars() does. Callers that want whole characters want codePoints().
	 */
	default IntStream chars() {
		int n = length();
		int[] out = new int[n];
		for (int i = 0; i < n; ++i) {
			out[i] = charAt(i);
		}
		return IntStream.of(out);
	}

	/**
	 * Code points, so a surrogate pair arrives as the one character it represents. Fonts
	 * care: this is what decides whether a glyph outside the basic plane is looked up once
	 * or twice.
	 *
	 * Built eagerly into an array rather than lazily. The strings this runs on are font
	 * definition rows and player names, so the array costs nothing, and a lazy spliterator
	 * would have to re-derive the pairing on every advance.
	 */
	default IntStream codePoints() {
		int n = length();
		int[] out = new int[n];
		int count = 0;
		int i = 0;
		while (i < n) {
			char c = charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < n) {
				char low = charAt(i + 1);
				if (Character.isLowSurrogate(low)) {
					out[count++] = Character.toCodePoint(c, low);
					i += 2;
					continue;
				}
			}
			out[count++] = c;
			++i;
		}
		if (count == n) {
			return IntStream.of(out);
		}
		int[] trimmed = new int[count];
		System.arraycopy(out, 0, trimmed, 0, count);
		return IntStream.of(trimmed);
	}
}
