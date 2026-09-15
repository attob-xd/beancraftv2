/**
 * Checks that the regexes rewritten for TeaVM still behave exactly like vanilla's.
 *
 *     java -cp "build/classes/java/main;<libs>" tools/VerifyRegexRewrites.java
 *
 * Two vanilla classes use the shorthands \h and \v, which TeaVM's regex engine cannot handle -
 * it rejects \h outright and silently reads \v as the letter v - so both were rewritten with
 * explicit character classes: com.mojang.blaze3d.preprocessor.GlslPreprocessor and
 * net.minecraft.util.StringUtil. tools/scan_bad_regex.py is what says those are the only two.
 *
 * The character classes themselves were checked against the JDK character by character, but
 * that is not the same as checking the assembled patterns: the rewrite also restructures
 * vanilla's inline literals into shared constants, and a stray paren or a dropped group would
 * change the meaning without changing any single class. So this compares behaviour instead.
 *
 * Every pattern is read reflectively out of the compiled class, so the test always sees what
 * the source actually produces rather than a copy that can drift, and each is run against
 * vanilla's original over every shader source that ships in the resource pack plus a set of
 * hand-written cases that exercise what real shaders do not - tabs, non-breaking spaces,
 * commented-out directives, and strings containing a literal 'v', which is the character the
 * broken \v would have keyed on.
 *
 * What this establishes: the rewrite is faithful. It runs on the JDK, where both forms
 * compile. Whether TeaVM's engine then agrees with the JDK on the rewritten form is a
 * separate question, and the answer to that one is the client rendering a menu.
 */
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class VerifyRegexRewrites {

	// Vanilla's originals, verbatim.
	static final Pattern V_MOJ_IMPORT = Pattern.compile(
		"(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*moj_import(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(?:\"(.*)\"|<(.*)>))");
	static final Pattern V_VERSION = Pattern.compile(
		"(#(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*version(?:/\\*(?:[^*]|\\*+[^*/])*\\*+/|\\h)*(\\d+))\\b");
	static final Pattern V_ENDS_WITH_WHITESPACE = Pattern.compile(
		"(?:^|\\v)(?:\\s|/\\*(?:[^*]|\\*+[^*/])*\\*+/|(//[^\\v]*))*\\z");
	static final Pattern V_LINE = Pattern.compile("\\r\\n|\\v");
	static final Pattern V_LINE_END = Pattern.compile("(?:\\r\\n|\\v)$");

	static int comparisons;
	static int mismatches;

	public static void main(String[] args) throws Exception {
		Class<?> glsl = Class.forName("com.mojang.blaze3d.preprocessor.GlslPreprocessor");
		Class<?> strUtil = Class.forName("net.minecraft.util.StringUtil");

		List<String> inputs = shaderSources();
		System.out.println("shader sources found: " + inputs.size());
		if (inputs.isEmpty()) {
			System.out.println("FAILED: no shader sources - the test would prove nothing");
			System.exit(1);
		}
		inputs.addAll(edgeCases());

		compareAll("MOJ_IMPORT", V_MOJ_IMPORT, field(glsl, "REGEX_MOJ_IMPORT"), inputs);
		compareAll("VERSION", V_VERSION, field(glsl, "REGEX_VERSION"), inputs);
		compareAll("ENDS_WITH_WHITESPACE", V_ENDS_WITH_WHITESPACE,
				field(glsl, "REGEX_ENDS_WITH_WHITESPACE"), inputs);
		compareAll("LINE", V_LINE, field(strUtil, "LINE_PATTERN"), inputs);
		compareAll("LINE_END", V_LINE_END, field(strUtil, "LINE_END_PATTERN"), inputs);

		// The two methods those patterns exist for, against a JDK reference.
		Method lineCount = strUtil.getMethod("lineCount", String.class);
		Method endsWithNewLine = strUtil.getMethod("endsWithNewLine", String.class);
		int rewritten = 0;
		for (String input : inputs) {
			int expectedLines = input.isEmpty() ? 0 : countMatches(V_LINE, input) + 1;
			int actualLines = (int) lineCount.invoke(null, input);
			++comparisons;
			if (expectedLines != actualLines) {
				System.out.println("MISMATCH lineCount: " + expectedLines + " vs " + actualLines
						+ " on " + show(input));
				++mismatches;
			}
			boolean expectedEnds = V_LINE_END.matcher(input).find();
			boolean actualEnds = (boolean) endsWithNewLine.invoke(null, input);
			++comparisons;
			if (expectedEnds != actualEnds) {
				System.out.println("MISMATCH endsWithNewLine: " + expectedEnds + " vs "
						+ actualEnds + " on " + show(input));
				++mismatches;
			}
			if (expectedLines > 1 || expectedEnds) {
				++rewritten;
			}
		}

		System.out.println(comparisons + " comparisons, " + mismatches + " mismatches");
		System.out.println(rewritten + " inputs actually exercised a line break");
		if (mismatches > 0 || rewritten == 0) {
			System.exit(1);
		}
		System.out.println("OK - every rewritten pattern matches vanilla's exactly");
	}

	static List<String> edgeCases() {
		List<String> out = new ArrayList<>();
		out.add("#moj_import <light.glsl>");
		out.add("  #moj_import \"a.glsl\"");
		out.add("//#moj_import <skipped.glsl>");
		out.add("/*#moj_import <skipped.glsl>*/");
		out.add("#/*c*/moj_import/*c*/<spaced.glsl>");
		out.add("#\tmoj_import\t<tabbed.glsl>");
		out.add("#\u00A0moj_import\u00A0<nbsp.glsl>");
		out.add("#version 150\n#moj_import <x.glsl>\n");
		// strings whose only vertical-whitespace-shaped content is a literal v, which is what
		// TeaVM's broken \v would have matched
		out.add("v#moj_import <v.glsl>");
		out.add("very verbose vertices");
		out.add("ends with v");
		// each vertical whitespace character on its own
		out.add("a\nb");
		out.add("a\u000Bb");
		out.add("a\fb");
		out.add("a\rb");
		out.add("a\r\nb");
		out.add("a\u0085b");
		out.add("a\u2028b");
		out.add("a\u2029b");
		out.add("trailing\n");
		out.add("trailing\r\n");
		out.add("trailing\u2029");
		out.add("x\u000Bx\n// trailing\n   ");
		out.add("");
		return out;
	}

	static int countMatches(Pattern p, String s) {
		Matcher m = p.matcher(s);
		int n = 0;
		while (m.find()) {
			++n;
		}
		return n;
	}

	static Pattern field(Class<?> cls, String name) throws Exception {
		Field f = cls.getDeclaredField(name);
		f.setAccessible(true);
		return (Pattern) f.get(null);
	}

	static void compareAll(String what, Pattern vanilla, Pattern mine, List<String> inputs) {
		System.out.println("rebuilt " + what + ": " + mine.pattern());
		for (String input : inputs) {
			mismatches += compare(what, vanilla, mine, input);
		}
	}

	/** Compares every match: position, and every group's value. */
	static int compare(String what, Pattern vanilla, Pattern mine, String input) {
		Matcher a = vanilla.matcher(input);
		Matcher b = mine.matcher(input);
		int bad = 0;
		while (true) {
			boolean foundA = a.find();
			boolean foundB = b.find();
			++comparisons;
			if (foundA != foundB) {
				System.out.println("MISMATCH " + what + ": find() disagreed (" + foundA + " vs "
						+ foundB + ") on " + show(input));
				return bad + 1;
			}
			if (!foundA) {
				return bad;
			}
			if (a.start() != b.start() || a.end() != b.end()) {
				System.out.println("MISMATCH " + what + ": span " + a.start() + ".." + a.end()
						+ " vs " + b.start() + ".." + b.end() + " on " + show(input));
				++bad;
			}
			if (a.groupCount() != b.groupCount()) {
				System.out.println("MISMATCH " + what + ": group count " + a.groupCount()
						+ " vs " + b.groupCount());
				return bad + 1;
			}
			for (int g = 0; g <= a.groupCount(); ++g) {
				String ga = a.group(g);
				String gb = b.group(g);
				if (ga == null ? gb != null : !ga.equals(gb)) {
					System.out.println("MISMATCH " + what + ": group " + g + " " + show(ga)
							+ " vs " + show(gb) + " on " + show(input));
					++bad;
				}
			}
		}
	}

	static String show(String s) {
		if (s == null) {
			return "<null>";
		}
		String t = s.length() > 70 ? s.substring(0, 70) + "..." : s;
		return "[" + t.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "]";
	}

	static List<String> shaderSources() throws IOException {
		Path root = Path.of("desktopRuntime/resources/assets/minecraft/shaders");
		List<String> out = new ArrayList<>();
		if (!Files.isDirectory(root)) {
			return out;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				String n = p.getFileName().toString();
				if (n.endsWith(".vsh") || n.endsWith(".fsh") || n.endsWith(".glsl")
						|| n.endsWith(".json")) {
					out.add(Files.readString(p));
				}
			}
		}
		return out;
	}
}
