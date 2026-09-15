package com.ibm.icu.lang;

/**
 * ICU4J's character database. Minecraft asks it for the mirrored form of a bracket when
 * laying out right-to-left text; see Bidi for why icu4j is not on the classpath. The JDK's
 * own Character covers everything else the client needs.
 */
public final class UCharacter {

	private UCharacter() {
	}

	/**
	 * Mirroring pairs for the brackets that actually appear in Minecraft's text. The real
	 * table is the full Unicode BidiMirroring.txt; this covers the ASCII pairs and returns
	 * anything else unchanged, which is what a left-to-right layout wants anyway.
	 */
	public static int getMirror(int codePoint) {
		switch (codePoint) {
		case '(': return ')';
		case ')': return '(';
		case '[': return ']';
		case ']': return '[';
		case '{': return '}';
		case '}': return '{';
		case '<': return '>';
		case '>': return '<';
		default: return codePoint;
		}
	}

	public static boolean isMirrored(int codePoint) {
		return getMirror(codePoint) != codePoint;
	}
}
