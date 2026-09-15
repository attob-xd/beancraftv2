/**
 * Differential test: net.minecraft.util.SimpleBitStorage as reproduced in src/game/java,
 * against the original class in libs-1.18.2/1.18.2-mapped.jar.
 *
 *     java tools/VerifyBitStorage.java <dir-with-compiled-reproduction>
 *
 * Why this exists rather than a read-through. The reproduction changes two calls -
 * Integer.toUnsignedLong(x) becomes (x & 0xFFFFFFFFL), because TeaVM's class library has no
 * such method - inside cellIndex, which every read and write of block, biome and light data
 * goes through. A mistake there does not throw. It returns the wrong cell, so a block reads
 * its neighbour's bits, and the damage shows up as corrupt chunks with nothing pointing back
 * here. Reasoning about it is not enough; this runs both classes side by side.
 *
 * Both are loaded in their own URLClassLoader, so the two same-named classes can coexist and
 * the reproduction is tested exactly as it sits in the source tree - no renaming, no copy.
 * Everything is driven by reflection for that reason.
 */
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.util.Arrays;
import java.util.Random;
import java.util.function.IntConsumer;

public class VerifyBitStorage {

	static final String CLS = "net.minecraft.util.SimpleBitStorage";
	static int checks = 0;

	static URL u(String p) throws Exception {
		return new File(p).toURI().toURL();
	}

	public static void main(String[] args) throws Exception {
		String mine = args.length > 0 ? args[0] : "build/verify-sbs";
		URL jar = u("libs-1.18.2/1.18.2-mapped.jar");
		URL lang = u("libs-1.18.2/commons-lang3-3.12.0.jar");
		ClassLoader parent = ClassLoader.getPlatformClassLoader();
		ClassLoader origL = new URLClassLoader(new URL[] { jar, lang }, parent);
		ClassLoader mineL = new URLClassLoader(new URL[] { u(mine), jar, lang }, parent);

		Class<?> orig = origL.loadClass(CLS);
		Class<?> repro = mineL.loadClass(CLS);
		// Guard against the classpath order silently giving us the jar's class twice.
		if (repro.getResource("SimpleBitStorage.class") != null
				&& orig.getResource("SimpleBitStorage.class") != null) {
			String a = String.valueOf(orig.getProtectionDomain().getCodeSource().getLocation());
			String b = String.valueOf(repro.getProtectionDomain().getCodeSource().getLocation());
			if (a.equals(b)) {
				throw new IllegalStateException("both loaders resolved to " + a
						+ " - the reproduction was not compiled into " + mine);
			}
			System.out.println("original     : " + a);
			System.out.println("reproduction : " + b);
		}

		long seed = 20250909L;
		for (int bits = 1; bits <= 32; ++bits) {
			for (int size : new int[] { 1, 7, 64, 4096, 5000 }) {
				try {
					compare(orig, repro, bits, size, new Random(seed++));
				} catch (Throwable t) {
					// A wrong cellIndex usually throws before it disagrees, so report the
					// width and size either way rather than letting a bare
					// ArrayIndexOutOfBoundsException out of a reflective call.
					Throwable cause = t;
					while (cause.getCause() != null) {
						cause = cause.getCause();
					}
					System.out.println("FAILED at bits=" + bits + " size=" + size + ": " + cause);
					cause.printStackTrace(System.out);
					System.exit(1);
				}
			}
		}
		System.out.println("OK - " + checks + " comparisons, all identical");
	}

	static void compare(Class<?> orig, Class<?> repro, int bits, int size, Random rnd)
			throws Exception {
		int[] values = new int[size];
		int bound = bits >= 31 ? Integer.MAX_VALUE : (1 << bits);
		for (int i = 0; i < size; ++i) {
			values[i] = rnd.nextInt(bound);
		}

		// 1. the packing constructor, which is how chunk data arrives from disk.
		Object a = newInstance(orig, bits, size, values);
		Object b = newInstance(repro, bits, size, values);
		eq(bits, size, "packing ctor raw", raw(a), raw(b));

		// 2. every read, which is where cellIndex is exercised hardest.
		for (int i = 0; i < size; ++i) {
			int x = (int) call(a, "get", i);
			int y = (int) call(b, "get", i);
			if (x != y) {
				fail(bits, size, "get(" + i + ")", x + " vs " + y);
			}
			if (x != values[i]) {
				fail(bits, size, "get(" + i + ")", "both returned " + x + ", packed " + values[i]);
			}
			++checks;
		}

		// 3. set and getAndSet on a fresh pair, in a scattered order.
		Object c = newInstance(orig, bits, size);
		Object d = newInstance(repro, bits, size);
		for (int n = 0; n < size; ++n) {
			int i = rnd.nextInt(size);
			int v = rnd.nextInt(bound);
			int x = (int) call(c, "getAndSet", i, v);
			int y = (int) call(d, "getAndSet", i, v);
			if (x != y) {
				fail(bits, size, "getAndSet(" + i + "," + v + ")", x + " vs " + y);
			}
			++checks;
		}
		eq(bits, size, "getAndSet raw", raw(c), raw(d));
		for (int n = 0; n < size; ++n) {
			int i = rnd.nextInt(size);
			int v = rnd.nextInt(bound);
			call(c, "set", i, v);
			call(d, "set", i, v);
		}
		eq(bits, size, "set raw", raw(c), raw(d));

		// 4. the two bulk readers.
		int[] ua = new int[size];
		int[] ub = new int[size];
		c.getClass().getMethod("unpack", int[].class).invoke(c, (Object) ua);
		d.getClass().getMethod("unpack", int[].class).invoke(d, (Object) ub);
		eq(bits, size, "unpack", ua, ub);

		int[] ga = new int[size];
		int[] gb = new int[size];
		collect(c, ga);
		collect(d, gb);
		eq(bits, size, "getAll", ga, gb);

		// 5. copy, and the long[] constructor that reads a copy's raw data back.
		Object ca = c.getClass().getMethod("copy").invoke(c);
		Object cb = d.getClass().getMethod("copy").invoke(d);
		eq(bits, size, "copy raw", raw(ca), raw(cb));
	}

	static void collect(Object o, final int[] out) throws Exception {
		final int[] at = { 0 };
		IntConsumer sink = v -> out[at[0]++] = v;
		o.getClass().getMethod("getAll", IntConsumer.class).invoke(o, sink);
		if (at[0] != out.length) {
			throw new IllegalStateException("getAll produced " + at[0] + " of " + out.length);
		}
	}

	static Object newInstance(Class<?> cls, int bits, int size) throws Exception {
		Constructor<?> ctor = cls.getConstructor(int.class, int.class);
		return ctor.newInstance(bits, size);
	}

	static Object newInstance(Class<?> cls, int bits, int size, int[] values) throws Exception {
		Constructor<?> ctor = cls.getConstructor(int.class, int.class, int[].class);
		return ctor.newInstance(bits, size, values);
	}

	static Object call(Object o, String name, int... args) throws Exception {
		Class<?>[] types = new Class<?>[args.length];
		Arrays.fill(types, int.class);
		Method m = o.getClass().getMethod(name, types);
		Object[] boxed = new Object[args.length];
		for (int i = 0; i < args.length; ++i) {
			boxed[i] = args[i];
		}
		return m.invoke(o, boxed);
	}

	static long[] raw(Object o) throws Exception {
		return (long[]) o.getClass().getMethod("getRaw").invoke(o);
	}

	static void eq(int bits, int size, String what, long[] a, long[] b) {
		if (!Arrays.equals(a, b)) {
			fail(bits, size, what, "long[" + a.length + "] differ at index " + firstDiff(a, b));
		}
		++checks;
	}

	static void eq(int bits, int size, String what, int[] a, int[] b) {
		if (!Arrays.equals(a, b)) {
			fail(bits, size, what, "int[" + a.length + "] differ at index " + firstDiff(a, b));
		}
		++checks;
	}

	static int firstDiff(long[] a, long[] b) {
		for (int i = 0; i < Math.min(a.length, b.length); ++i) {
			if (a[i] != b[i]) {
				return i;
			}
		}
		return -1;
	}

	static int firstDiff(int[] a, int[] b) {
		for (int i = 0; i < Math.min(a.length, b.length); ++i) {
			if (a[i] != b[i]) {
				return i;
			}
		}
		return -1;
	}

	static void fail(int bits, int size, String what, String detail) {
		throw new AssertionError(
				"MISMATCH bits=" + bits + " size=" + size + " " + what + ": " + detail);
	}
}
