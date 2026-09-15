import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Differential test for the {@code %f} conversion added to
 * {@code org.teavm.classlib.java.util.TFormatter}.
 *
 * <p>Every combination below is formatted twice - once by the patched TFormatter, once by the
 * JDK's own {@code String.format} - and the two strings must be identical. The point is that
 * the flag handling (sign, parentheses, zero padding, width, grouping, left justification) is
 * fiddly enough that reading it proves nothing; the previous formatter bugs in this port were
 * all found this way.
 *
 * <p>What this does and does not cover: TFormatter is loaded on a desktop JVM, so the digits
 * come from the JDK's {@code DecimalFormat} rather than TeaVM's. That makes this a test of the
 * conversion logic - which is the part that was written here - and not of TeaVM's
 * {@code DecimalFormat}, which is its own code and already used by the {@code %d} path.
 *
 * <p>Run it through {@code sh tools/verify_format_float.sh}, which supplies the classpath.
 */
public class VerifyFormatFloat {

	public static void main(String[] args) throws Exception {
		Class<?> cls = Class.forName("org.teavm.classlib.java.util.TFormatter");
		Method format = cls.getMethod("format", Locale.class, String.class, Object[].class);
		Method toString = cls.getMethod("toString");

		double[] values = {
			0.0, -0.0, 1.0, -1.0, 0.5, -0.5, 3.14159265358979, -3.14159265358979,
			2.5, 3.5, -2.5, 0.05, 0.049999, 123456.789, -123456.789,
			1.0 / 3.0, 2.0 / 3.0, 1e7, -1e7, 99.995, 0.0001, 1234567.0,
			Double.MAX_VALUE / 1e300, 1e-7, 42.0, -42.0
		};

		String[] patterns = {
			"%f", "%.0f", "%.1f", "%.2f", "%.3f", "%.5f", "%.7f",
			"%10.2f", "%-10.2f", "%010.2f", "%+.2f", "%+010.2f",
			"% .2f", "%(.2f", "%(010.2f", "%,.2f", "%,012.2f", "%,.0f",
			"%15.4f", "%-15.4f", "%015.4f"
		};

		int compared = 0;
		List<String> mismatches = new ArrayList<>();

		for (String pattern : patterns) {
			for (double value : values) {
				String expected;
				try {
					expected = String.format(Locale.ROOT, pattern, value);
				} catch (RuntimeException ex) {
					expected = "THREW " + ex.getClass().getName();
				}

				String actual;
				try {
					Object f = cls.getDeclaredConstructor().newInstance();
					format.invoke(f, Locale.ROOT, pattern, new Object[] { value });
					actual = (String) toString.invoke(f);
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					actual = "THREW " + cause.getClass().getName();
				}

				++compared;
				if (!expected.equals(actual)) {
					mismatches.add(String.format(
							Locale.ROOT, "  %-10s %-22s jdk=[%s] ours=[%s]",
							pattern, Double.toString(value), expected, actual));
				}
			}
		}

		// Non-finite values and a float (not double) argument, which takes the same path.
		Object[][] specials = {
			{ "%f", Double.NaN }, { "%.2f", Double.NaN },
			{ "%f", Double.POSITIVE_INFINITY }, { "%f", Double.NEGATIVE_INFINITY },
			{ "%10.2f", Double.POSITIVE_INFINITY }, { "%(.2f", Double.NEGATIVE_INFINITY },
			{ "%.2f", 1.5f }, { "%.4f", -0.125f }, { "%8.2f", 7.5f }
		};
		for (Object[] pair : specials) {
			String pattern = (String) pair[0];
			Object value = pair[1];
			String expected;
			try {
				expected = String.format(Locale.ROOT, pattern, value);
			} catch (RuntimeException ex) {
				expected = "THREW " + ex.getClass().getName();
			}
			String actual;
			try {
				Object f = cls.getDeclaredConstructor().newInstance();
				format.invoke(f, Locale.ROOT, pattern, new Object[] { value });
				actual = (String) toString.invoke(f);
			} catch (Exception ex) {
				Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
				actual = "THREW " + cause.getClass().getName();
			}
			++compared;
			if (!expected.equals(actual)) {
				mismatches.add(String.format(Locale.ROOT, "  %-10s %-22s jdk=[%s] ours=[%s]",
						pattern, String.valueOf(value), expected, actual));
			}
		}

		System.out.println("compared " + compared + " format/value pairs");
		if (mismatches.isEmpty()) {
			System.out.println("no mismatches");
		} else {
			System.out.println(mismatches.size() + " MISMATCHES:");
			for (String m : mismatches) {
				System.out.println(m);
			}
		}

		// Negative control: a pattern the formatter must still refuse. If this passes, the test
		// is not actually reaching the new code and the run above proved nothing.
		String control;
		try {
			Object f = cls.getDeclaredConstructor().newInstance();
			format.invoke(f, Locale.ROOT, "%e", new Object[] { 1.0 });
			control = "accepted %e - CONTROL FAILED, it should be unimplemented";
		} catch (Exception ex) {
			Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
			control = "rejected %e with " + cause.getClass().getSimpleName() + " - control ok";
		}
		System.out.println("negative control: " + control);

		if (!mismatches.isEmpty()) {
			System.exit(1);
		}
	}
}
