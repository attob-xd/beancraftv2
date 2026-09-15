package org.teavm.classlib.java.net;

/**
 * Missing from TeaVM's class library. IDN converts internationalised host names to
 * punycode. The browser does that itself before any request leaves the page, and
 * EaglercraftX never builds a host name of its own, so the ASCII form is the input.
 */
public final class TIDN {

	public static final int ALLOW_UNASSIGNED = 1;
	public static final int USE_STD3_ASCII_RULES = 2;

	private TIDN() {
	}

	public static String toASCII(String input) {
		return input;
	}

	public static String toASCII(String input, int flag) {
		return input;
	}

	public static String toUnicode(String input) {
		return input;
	}

	public static String toUnicode(String input, int flag) {
		return input;
	}
}
