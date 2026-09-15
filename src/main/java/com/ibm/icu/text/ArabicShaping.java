package com.ibm.icu.text;

/**
 * ICU4J's Arabic shaper, which picks the right contextual glyph form for each letter.
 *
 * The real icu4j jar is 13 MB of code and locale data, almost all of it unreachable here,
 * and compiling it would add minutes to every build for one text feature. This shim keeps
 * the two call sites (Font and FormattedBidiReorder) linking.
 *
 * The honest cost: Arabic and other cursive scripts render in their isolated forms rather
 * than joined. Latin, Cyrillic and CJK are unaffected, because they never shape.
 */
public final class ArabicShaping {

	public static final int LENGTH_GROW_SHRINK = 0;
	public static final int LETTERS_SHAPE = 8;
	public static final int TEXT_DIRECTION_VISUAL_LTR = 4;
	public static final int DIGITS_NOOP = 0;

	public ArabicShaping(int options) {
	}

	public String shape(String text) {
		return text;
	}
}
