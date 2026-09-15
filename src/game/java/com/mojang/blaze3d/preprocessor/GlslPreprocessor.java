package com.mojang.blaze3d.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import net.minecraft.FileUtil;
import net.minecraft.util.StringUtil;

/**
 * Vanilla's class, with one change: the regex shorthands \h and \v are written out.
 *
 * TeaVM's regex engine does not support either, and it fails in two different ways.
 *
 *   - \h it *rejects*. TLexer has an explicit list of escape characters it throws on and 'h'
 *     is one of them, so Pattern.compile raised TPatternSyntaxException - with an empty
 *     description, so the log carried only an index and the pattern. Every shader failed to
 *     compile and the client died with "could not preload blit shader" before drawing a menu.
 *   - \v it *accepts and gets wrong*, which is worse. 'v' falls into the lexer's
 *     literal-escape group, so \v matches a lowercase letter v and [^\v] means "anything but
 *     v". REGEX_ENDS_WITH_WHITESPACE below is what decides whether a #moj_import is real code
 *     or sits inside a comment; with \v broken it would have answered that question against
 *     the wrong characters and silently mis-preprocessed shader source.
 *
 * The replacements are the JDK's own definitions, not approximations:
 *
 *     \h  ->  space, tab, U+00A0, U+1680, U+180E, U+2000-U+200A, U+202F, U+205F, U+3000
 *     \v  ->  U+000A, U+000B, U+000C, U+000D, U+0085, U+2028, U+2029
 *
 * Both were checked character by character against JDK 17's \h and \v over the whole BMP and
 * agree exactly. TeaVM does support four-hex-digit unicode escapes, so nothing here needs
 * narrowing to ASCII.
 *
 * This is the only class in vanilla 1.18.2 that uses either shorthand -
 * tools/scan_bad_regex.py checks all 4,241 files and finds these two patterns and no others -
 * so replacing it covers the whole category.
 *
 * Everything else is vanilla's logic unchanged.
 */
public abstract class GlslPreprocessor {

	/** The JDK's \h, written out; see the class comment. */
	private static final String HORIZONTAL_WS =
			"[ \\t\\u00A0\\u1680\\u180E\\u2000-\\u200A\\u202F\\u205F\\u3000]";

	/** The JDK's \v, written out; see the class comment. */
	private static final String VERTICAL_WS = "[\\n\\u000B\\f\\r\\u0085\\u2028\\u2029]";

	/**
	 * The negation is spelled out rather than built as "[^" + VERTICAL_WS + "]", which would
	 * nest a character class inside a negated one. Java accepts that; there is no reason to
	 * find out whether TeaVM's engine does, when writing it out costs one line.
	 */
	private static final String NOT_VERTICAL_WS = "[^\\n\\u000B\\f\\r\\u0085\\u2028\\u2029]";

	private static final String C_COMMENT = "/\\*(?:[^*]|\\*+[^*/])*\\*+/";
	private static final String LINE_COMMENT = "//" + NOT_VERTICAL_WS + "*";

	/**
	 * Vanilla writes these three inline. They are assembled from the pieces above so the two
	 * substitutions appear once each rather than six times, and so the shape stays readable.
	 * The strings produced are character-for-character vanilla's with \h and \v expanded.
	 */
	private static final String SKIPPABLE = "(?:" + C_COMMENT + "|" + HORIZONTAL_WS + ")*";

	private static final Pattern REGEX_MOJ_IMPORT = Pattern.compile(
			"(#" + SKIPPABLE + "moj_import" + SKIPPABLE + "(?:\"(.*)\"|<(.*)>))");

	private static final Pattern REGEX_VERSION = Pattern.compile(
			"(#" + SKIPPABLE + "version" + SKIPPABLE + "(\\d+))\\b");

	private static final Pattern REGEX_ENDS_WITH_WHITESPACE = Pattern.compile(
			"(?:^|" + VERTICAL_WS + ")(?:\\s|" + C_COMMENT + "|(" + LINE_COMMENT + "))*\\z");

	public List<String> process(String source) {
		GlslPreprocessor.Context context = new GlslPreprocessor.Context();
		List<String> parts = this.processImports(source, context, "");
		parts.set(0, this.setVersion(parts.get(0), context.glslVersion));
		return parts;
	}

	private List<String> processImports(String source, GlslPreprocessor.Context context,
			String prefix) {
		int sourceId = context.sourceId;
		int cursor = 0;
		String lineDirective = "";
		ArrayList<String> out = Lists.newArrayList();
		Matcher matcher = REGEX_MOJ_IMPORT.matcher(source);

		while (matcher.find()) {
			if (!isDirectiveDisabled(source, matcher, cursor)) {
				String name = matcher.group(2);
				boolean quoted = name != null;
				if (!quoted) {
					name = matcher.group(3);
				}

				if (name != null) {
					String before = source.substring(cursor, matcher.start(1));
					String path = prefix + name;
					String imported = this.applyImport(quoted, path);
					if (!Strings.isNullOrEmpty(imported)) {
						if (!StringUtil.endsWithNewLine(imported)) {
							imported = imported + System.lineSeparator();
						}

						context.sourceId++;
						int importedId = context.sourceId;
						List<String> importedParts = this.processImports(imported, context,
								quoted ? FileUtil.getFullResourcePath(path) : "");
						importedParts.set(0, String.format(Locale.ROOT, "#line %d %d\n%s", 0,
								importedId, this.processVersions(importedParts.get(0), context)));
						if (!StringUtils.isBlank(before)) {
							out.add(before);
						}

						out.addAll(importedParts);
					} else {
						String stub = quoted
								? String.format("/*#moj_import \"%s\"*/", name)
								: String.format("/*#moj_import <%s>*/", name);
						out.add(lineDirective + before + stub);
					}

					int line = StringUtil.lineCount(source.substring(0, matcher.end(1)));
					lineDirective = String.format(Locale.ROOT, "#line %d %d", line, sourceId);
					cursor = matcher.end(1);
				}
			}
		}

		String rest = source.substring(cursor);
		if (!StringUtils.isBlank(rest)) {
			out.add(lineDirective + rest);
		}

		return out;
	}

	private String processVersions(String source, GlslPreprocessor.Context context) {
		Matcher matcher = REGEX_VERSION.matcher(source);
		if (matcher.find() && isDirectiveEnabled(source, matcher)) {
			context.glslVersion = Math.max(context.glslVersion,
					Integer.parseInt(matcher.group(2)));
			return source.substring(0, matcher.start(1)) + "/*"
					+ source.substring(matcher.start(1), matcher.end(1)) + "*/"
					+ source.substring(matcher.end(1));
		} else {
			return source;
		}
	}

	private String setVersion(String source, int glslVersion) {
		Matcher matcher = REGEX_VERSION.matcher(source);
		return matcher.find() && isDirectiveEnabled(source, matcher)
				? source.substring(0, matcher.start(2))
						+ Math.max(glslVersion, Integer.parseInt(matcher.group(2)))
						+ source.substring(matcher.end(2))
				: source;
	}

	private static boolean isDirectiveEnabled(String source, Matcher matcher) {
		return !isDirectiveDisabled(source, matcher, 0);
	}

	private static boolean isDirectiveDisabled(String source, Matcher matcher, int from) {
		int offset = matcher.start() - from;
		if (offset == 0) {
			return false;
		} else {
			Matcher tail = REGEX_ENDS_WITH_WHITESPACE
					.matcher(source.substring(from, matcher.start()));
			if (!tail.find()) {
				return true;
			} else {
				int end = tail.end(1);
				return end == matcher.start();
			}
		}
	}

	@Nullable
	public abstract String applyImport(boolean quoted, String path);

	static final class Context {
		int glslVersion;
		int sourceId;
	}
}
