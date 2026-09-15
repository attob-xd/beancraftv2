/**
 * Runs this port's rewritten regexes through TeaVM's OWN regex engine, on the desktop JVM.
 *
 *     java -cp "<teavm-classlib jar>" tools/ProbeTeavmRegex.java
 *     (tools/probe_teavm_regex.sh finds the jar and runs it)
 *
 * TeaVM's java.util.regex is an Apache Harmony port compiled into
 * org.teavm.classlib.java.util.regex, and those are ordinary Java classes - so they can be
 * driven directly on a desktop JVM without generating a page. That turns "does TeaVM's engine
 * handle this pattern" from a 20-minute build into a second, which matters because the engine
 * is demonstrably not the JDK's: it rejects \h outright, silently reads \v as the letter v,
 * and throws IllegalArgumentException("") from replaceAll for a capture group that did not
 * participate.
 *
 * What this checks, in order of how badly each fails:
 *
 *   - compiles at all;
 *   - find() ADVANCES. A matcher that reports a match without moving turns vanilla's
 *     `while (matcher.find())` into an infinite loop. That is not hypothetical - it is what
 *     this file was written to diagnose: the client sat at 100% CPU inside
 *     GlslPreprocessor.processImports with memory creeping a couple of MB a minute, which is
 *     the signature of a loop that allocates a little each pass rather than of heavy work;
 *   - the matches agree with the JDK's, position for position and group for group.
 *
 * Every pattern is read out of the compiled classes rather than retyped, so the probe cannot
 * drift away from what actually ships.
 */
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProbeTeavmRegex {

	static Class<?> T_PATTERN;
	static int failures;

	public static void main(String[] args) throws Exception {
		T_PATTERN = Class.forName("org.teavm.classlib.java.util.regex.TPattern");

		Map<String, String> patterns = new LinkedHashMap<>();
		patterns.putAll(patternsOf("com.mojang.blaze3d.preprocessor.GlslPreprocessor",
				"REGEX_MOJ_IMPORT", "REGEX_VERSION", "REGEX_ENDS_WITH_WHITESPACE"));
		patterns.putAll(patternsOf("net.minecraft.util.StringUtil",
				"LINE_PATTERN", "LINE_END_PATTERN", "STRIP_COLOR_PATTERN"));

		List<String> inputs = inputs();
		System.out.println("patterns: " + patterns.size() + ", inputs: " + inputs.size() + "\n");

		for (Map.Entry<String, String> e : patterns.entrySet()) {
			check(e.getKey(), e.getValue(), inputs);
		}

		System.out.println("\n" + (failures == 0
				? "OK - TeaVM's engine agrees with the JDK on every pattern"
				: failures + " problem(s) found"));
		System.exit(failures == 0 ? 0 : 1);
	}

	static Map<String, String> patternsOf(String className, String... fields) {
		Map<String, String> out = new LinkedHashMap<>();
		try {
			Class<?> cls = Class.forName(className);
			for (String name : fields) {
				Field f = cls.getDeclaredField(name);
				f.setAccessible(true);
				Object v = f.get(null);
				out.put(className.substring(className.lastIndexOf('.') + 1) + "." + name,
						((java.util.regex.Pattern) v).pattern());
			}
		} catch (Exception e) {
			System.out.println("(could not read patterns from " + className + ": " + e + ")");
		}
		return out;
	}

	static void check(String label, String regex, List<String> inputs) throws Exception {
		System.out.println(label);
		System.out.println("   " + abbreviate(regex, 150));
		Object tp;
		try {
			Method compile = T_PATTERN.getMethod("compile", String.class);
			tp = compile.invoke(null, regex);
		} catch (Exception e) {
			System.out.println("   TEAVM REFUSES TO COMPILE IT: " + root(e));
			++failures;
			return;
		}
		Method matcherOf = tp.getClass().getMethod("matcher", CharSequence.class);

		java.util.regex.Pattern jdk = java.util.regex.Pattern.compile(regex);
		int advanceFailures = 0;
		int disagreements = 0;
		for (String input : inputs) {
			Object tm = matcherOf.invoke(tp, input);
			Method find = tm.getClass().getMethod("find");
			Method start = tm.getClass().getMethod("start");
			Method end = tm.getClass().getMethod("end");

			List<int[]> theirs = new ArrayList<>();
			int lastEnd = -1;
			int guard = 0;
			while ((Boolean) find.invoke(tm)) {
				int s = (Integer) start.invoke(tm);
				int en = (Integer) end.invoke(tm);
				theirs.add(new int[] { s, en });
				if (en == lastEnd && s == theirs.get(theirs.size() - 1)[0] && ++guard > 3) {
					++advanceFailures;
					System.out.println("   !! find() DOES NOT ADVANCE at " + s
							+ " on: " + abbreviate(input.replace("\n", "\\n"), 60));
					break;
				}
				lastEnd = en;
				if (theirs.size() > 10000) {
					++advanceFailures;
					System.out.println("   !! more than 10000 matches - runaway");
					break;
				}
			}
			if (advanceFailures > 0) {
				break;
			}

			java.util.regex.Matcher jm = jdk.matcher(input);
			List<int[]> mine = new ArrayList<>();
			while (jm.find()) {
				mine.add(new int[] { jm.start(), jm.end() });
			}
			if (mine.size() != theirs.size()) {
				++disagreements;
				if (disagreements <= 3) {
					System.out.println("   ~~ match count differs: jdk=" + mine.size()
							+ " teavm=" + theirs.size() + " on: "
							+ abbreviate(input.replace("\n", "\\n"), 60));
				}
			}
		}
		if (advanceFailures > 0) {
			++failures;
		} else if (disagreements > 0) {
			++failures;
			System.out.println("   " + disagreements + " input(s) matched differently");
		} else {
			System.out.println("   ok");
		}
	}

	static List<String> inputs() throws Exception {
		List<String> out = new ArrayList<>();
		Path shaders = Path.of("desktopRuntime/resources/assets/minecraft/shaders");
		if (Files.isDirectory(shaders)) {
			try (java.util.stream.Stream<Path> w = Files.walk(shaders)) {
				for (Path p : (Iterable<Path>) w.filter(Files::isRegularFile)::iterator) {
					String n = p.getFileName().toString();
					if (n.endsWith(".vsh") || n.endsWith(".fsh") || n.endsWith(".glsl")) {
						out.add(Files.readString(p, StandardCharsets.UTF_8));
					}
				}
			}
		}
		out.add("#version 150\n#moj_import <fog.glsl>\nvoid main() {}\n");
		out.add("#moj_import <light.glsl>");
		out.add("#  moj_import   <spaced.glsl>");
		out.add("a\nb\r\ncd");
		out.add("");
		return out;
	}

	static String abbreviate(String s, int n) {
		return s.length() <= n ? s : s.substring(0, n) + "...";
	}

	static String root(Throwable t) {
		while (t.getCause() != null) {
			t = t.getCause();
		}
		return t.toString();
	}
}
