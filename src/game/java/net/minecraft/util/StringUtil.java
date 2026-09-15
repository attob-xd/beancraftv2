package net.minecraft.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

/**
 * Vanilla's class, with one change: the regex shorthand \v is written out.
 *
 * TeaVM's regex engine does not reject \v - it reads it as the literal letter v, because 'v'
 * falls into TLexer's literal-escape group. So vanilla's two line patterns, "\r\n|\v" and
 * "(?:\r\n|\v)$", would have meant "a CRLF, or a lowercase v" instead of "a CRLF, or any
 * vertical whitespace". Nothing throws; the answers are just wrong, in both directions:
 *
 *   - lineCount("a\nb") returns 1 rather than 2, because a bare LF is not matched at all.
 *   - lineCount("very") returns 2, counting the v.
 *   - endsWithNewLine(s) is false for a string that does end in a newline, and true for one
 *     that ends in "v".
 *
 * That is not academic. GlslPreprocessor is the only caller of both, and it uses lineCount to
 * emit "#line N" directives into shader source and endsWithNewLine to decide whether an
 * imported chunk needs a trailing newline before the next directive is appended. Wrong
 * answers there produce shaders that either report errors against the wrong lines or fail to
 * compile because a #line directive was glued onto the end of a statement.
 *
 * The replacement is the JDK's own definition of \v, checked character by character against
 * JDK 17 over the whole BMP:
 *
 *     \v  ->  U+000A, U+000B, U+000C, U+000D, U+0085, U+2028, U+2029
 *
 * tools/scan_bad_regex.py reports this class and GlslPreprocessor as the only two in vanilla
 * 1.18.2 that use \v or \h at all.
 *
 * STRIP_COLOR_PATTERN is left exactly as vanilla writes it, and it is worth saying why it is
 * safe when the two above are not. Its backslash is doubled, so javac hands the regex engine
 * a six-character escape sequence rather than a section sign, and it is the *regex* engine
 * that decodes it. TeaVM implements that escape - its lexer reads four hex digits after the
 * u - and it implements the inline (?i) flag, so nothing there needs changing.
 */
public class StringUtil {

	private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

	/** The JDK's \v, written out; see the class comment. */
	private static final String VERTICAL_WS = "[\\n\\u000B\\f\\r\\u0085\\u2028\\u2029]";

	private static final Pattern LINE_PATTERN = Pattern.compile("\\r\\n|" + VERTICAL_WS);
	private static final Pattern LINE_END_PATTERN =
			Pattern.compile("(?:\\r\\n|" + VERTICAL_WS + ")$");

	public static String formatTickDuration(int ticks) {
		int seconds = ticks / 20;
		int minutes = seconds / 60;
		seconds %= 60;
		return seconds < 10 ? minutes + ":0" + seconds : minutes + ":" + seconds;
	}

	public static String stripColor(String text) {
		return STRIP_COLOR_PATTERN.matcher(text).replaceAll("");
	}

	public static boolean isNullOrEmpty(@Nullable String text) {
		return StringUtils.isEmpty(text);
	}

	public static String truncateStringIfNecessary(String text, int max, boolean ellipsis) {
		if (text.length() <= max) {
			return text;
		} else {
			return ellipsis && max > 3 ? text.substring(0, max - 3) + "..."
					: text.substring(0, max);
		}
	}

	public static int lineCount(String text) {
		if (text.isEmpty()) {
			return 0;
		} else {
			Matcher matcher = LINE_PATTERN.matcher(text);
			int lines = 1;

			while (matcher.find()) {
				lines++;
			}

			return lines;
		}
	}

	public static boolean endsWithNewLine(String text) {
		return LINE_END_PATTERN.matcher(text).find();
	}
}
