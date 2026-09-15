package org.teavm.classlib.java.lang;

import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.Properties;

import org.teavm.backend.javascript.spi.GeneratedBy;
import org.teavm.classlib.impl.console.StderrOutputStream;
import org.teavm.classlib.impl.console.StdoutOutputStream;
import org.teavm.classlib.java.io.TConsole;
import org.teavm.classlib.java.io.TInputStream;
import org.teavm.classlib.java.io.TOutputStream;
import org.teavm.classlib.java.io.TPrintStream;
import org.teavm.classlib.java.lang.reflect.TArray;
import org.teavm.interop.NoSideEffects;
import org.teavm.jso.browser.Performance;

/**
 * TeaVM's own {@code System}, reproduced for one reason: to add {@link #exit(int)}.
 *
 * <p>TeaVM's version has no {@code exit}, and that absence was not a quiet one.
 * {@code Minecraft.crash()} - the function that runs after <i>any</i> unhandled exception, once
 * it has written the crash report - ends with {@code System.exit(-1)}. So every crash in this
 * client produced a <b>second</b> crash, {@code NoSuchMethodError: System.exit(I)V}, and that
 * second one is what EaglercraftX's overlay displayed. The real failure was never shown: it had
 * to be dug out of the browser console, and it cost three separate debugging cycles before this
 * was worth fixing properly.
 *
 * <p><b>What was changed from TeaVM's class, and what was not.</b> Everything here is TeaVM's
 * behaviour, with the C and WebAssembly backends' code paths removed - this build emits
 * JavaScript and nothing else, so those paths were unreachable, and dropping them removes the
 * dependency on {@code org.teavm.runtime.*} (the GC, allocator and raw {@code Address} types)
 * that a faithful copy would otherwise need.
 *
 * <ul>
 * <li>{@code doArrayCopy} and {@code currentTimeMillis} keep
 *     {@code @GeneratedBy(SystemNativeGenerator.class)}, which is what supplies their
 *     implementation on the JavaScript backend. That generator lives in this same package in
 *     TeaVM's jar, which is why this class must be in this package and not merely named like
 *     it. Their {@code @DelegateTo} low-level twins are gone with the C backend.</li>
 * <li>{@code arraycopy} is verbatim, deliberately. It is the most load-bearing method in the
 *     class library, and its array-store checking is subtle enough that paraphrasing it would
 *     be a bad trade.</li>
 * <li>{@code nanoTime} keeps only the browser branch, {@code Performance.now()}.</li>
 * <li>{@code gc()} does nothing, as it already did on this backend.</li>
 * <li>The temp and home directories are the constants TeaVM uses off the C backend.</li>
 * </ul>
 *
 * <p>If something here is wrong the client will not start at all, which is the failure mode to
 * hope for: {@code arraycopy} and {@code currentTimeMillis} are used before the first frame.
 */
public final class TSystem extends TObject {
	private static TPrintStream outCache;
	private static TPrintStream errCache;
	private static TInputStream inCache;
	private static Properties properties;

	private TSystem() {
	}

	public static TPrintStream out() {
		if (outCache == null) {
			outCache = new TPrintStream(asTeaVMStream(StdoutOutputStream.INSTANCE), false);
		}
		return outCache;
	}

	public static TPrintStream err() {
		if (errCache == null) {
			errCache = new TPrintStream(asTeaVMStream(StderrOutputStream.INSTANCE), false);
		}
		return errCache;
	}

	public static TInputStream in() {
		if (inCache == null) {
			inCache = new TConsoleInputStream();
		}
		return inCache;
	}

	public static TConsole console() {
		return null;
	}

	public static TSecurityManager getSecurityManager() {
		return new TSecurityManager();
	}

	/**
	 * The method this whole class exists for.
	 *
	 * <p>There is no process to end. A page's script either returns or throws, and the tab
	 * stays open either way, so "exit" cannot mean what it means on a JVM. What the callers
	 * actually want is for the game to stop, and they have all finished their own work by the
	 * time they call it - {@code Minecraft.crash()} has already written and logged the crash
	 * report.
	 *
	 * <p>So it tells EaglercraftX the game is going down, which is the one thing that can be
	 * honestly done, and returns. Returning rather than throwing is the point: throwing is what
	 * the missing method already did, and it is precisely what buried the real crash.
	 *
	 * <p>Returning has one consequence that has to be handled here, though, because nothing else
	 * will: EaglercraftX puts its crash overlay up from {@code ClientMain}, and only when a
	 * throwable escapes {@code appMain}. If this simply returns, {@code Minecraft.crash} returns,
	 * the game loop unwinds normally, and the player is left looking at a frozen canvas with no
	 * statement that anything went wrong. The exit code is what separates the two cases - vanilla
	 * passes 0 from the clean-shutdown path in {@code Minecraft.run}'s {@code finally}, and -1 or
	 * -2 from {@code crash} - so a nonzero code raises the overlay and zero does not.
	 *
	 * <p>The overlay deliberately does not try to restate the crash. By the time this runs,
	 * {@code crash} has already put the full report through {@code Bootstrap.realStdoutPrintln}
	 * and saved it, so the report exists and the useful thing is to say where.
	 */
	public static void exit(int code) {
		net.lax1dude.eaglercraft.internal.PlatformRuntime.exit();
		if (code == 0) {
			return;
		}
		try {
			String nl = lineSeparator();
			net.lax1dude.eaglercraft.internal.teavm.ClientMain.showCrashScreen(
					"The game stopped after a crash (exit code " + code + ")." + nl + nl
							+ "Minecraft wrote its crash report before exiting - it is in the"
							+ " browser console, and in the crash-reports folder of this"
							+ " world's storage.");
		} catch (Throwable ignored) {
			// The crash screen failing must not replace the crash it is reporting; that is the
			// exact mistake this whole class exists to undo.
		}
	}

	/**
	 * Launders a real {@link java.io.OutputStream} into TeaVM's {@code TOutputStream}.
	 *
	 * <p>These two types are the same type - TeaVM renames its {@code T}-prefixed classes onto the
	 * JDK ones when it links - but that renaming happens long after javac has had its say, and to
	 * javac they are unrelated classes, so the direct cast TeaVM's own source uses is an error
	 * here. Going through {@code Object} compiles and emits exactly the same {@code checkcast},
	 * which after linking checks {@code java.io.OutputStream} against a stream that is one.
	 *
	 * <p>The same laundering is applied to the {@code (Object[]) src} cast in
	 * {@link #arraycopy}, for the same reason: {@code TObject} becomes {@code java.lang.Object}.
	 */
	private static TOutputStream asTeaVMStream(Object stream) {
		return (TOutputStream) stream;
	}

	public static void arraycopy(TObject src, int srcPos, TObject dest, int destPos, int length) {
		if (src != null && dest != null) {
			if (srcPos >= 0 && destPos >= 0 && length >= 0 && srcPos + length <= TArray.getLength(src)
					&& destPos + length <= TArray.getLength(dest)) {
				if (src != dest) {
					Class<?> srcType = src.getClass().getComponentType();
					Class<?> targetType = dest.getClass().getComponentType();
					if (srcType == null || targetType == null) {
						throw new TArrayStoreException();
					}

					if (srcType != targetType) {
						if (!srcType.isPrimitive() && !targetType.isPrimitive()) {
							Object[] srcArray = (Object[]) (Object) src;
							int pos = srcPos;

							for (int i = 0; i < length; i++) {
								Object elem = srcArray[pos++];
								if (!targetType.isInstance(elem)) {
									doArrayCopy(src, srcPos, dest, destPos, i);
									throw new TArrayStoreException();
								}
							}

							doArrayCopy(src, srcPos, dest, destPos, length);
							return;
						}

						if (!srcType.isPrimitive() || !targetType.isPrimitive()) {
							throw new TArrayStoreException();
						}
					}
				}

				doArrayCopy(src, srcPos, dest, destPos, length);
			} else {
				throw new TIndexOutOfBoundsException();
			}
		} else {
			throw new TNullPointerException("Either src or dest is null");
		}
	}

	static void fastArraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
		if (srcPos >= 0 && destPos >= 0 && length >= 0 && srcPos + length <= Array.getLength(src)
				&& destPos + length <= Array.getLength(dest)) {
			doArrayCopy(src, srcPos, dest, destPos, length);
		} else {
			throw new TIndexOutOfBoundsException();
		}
	}

	/** Implemented by TeaVM's own generator on the JavaScript backend; see the class comment. */
	@GeneratedBy(SystemNativeGenerator.class)
	@NoSideEffects
	static native void doArrayCopy(Object src, int srcPos, Object dest, int destPos, int length);

	/** Implemented by TeaVM's own generator on the JavaScript backend; see the class comment. */
	@GeneratedBy(SystemNativeGenerator.class)
	@NoSideEffects
	public static native long currentTimeMillis();

	private static void initPropertiesIfNeeded() {
		if (properties == null) {
			Properties defaults = new Properties();
			defaults.put("java.version", "1.8");
			defaults.put("os.name", "TeaVM");
			defaults.put("file.separator", "/");
			defaults.put("path.separator", ":");
			defaults.put("line.separator", lineSeparator());
			defaults.put("java.io.tmpdir", "/tmp");
			defaults.put("java.vm.version", "1.8");
			defaults.put("user.home", "/");
			properties = new Properties(defaults);
		}
	}

	public static String getProperty(String key) {
		initPropertiesIfNeeded();
		return properties.getProperty(key);
	}

	public static String getProperty(String key, String def) {
		String value = getProperty(key);
		return value != null ? value : def;
	}

	public static Properties getProperties() {
		initPropertiesIfNeeded();
		Properties result = new Properties();
		copyProperties(properties, result);
		return result;
	}

	public static void setProperties(Properties props) {
		initPropertiesIfNeeded();
		copyProperties(props, properties);
	}

	private static void copyProperties(Properties from, Properties to) {
		to.clear();
		if (from != null) {
			Enumeration<?> e = from.propertyNames();
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement();
				to.setProperty(key, from.getProperty(key));
			}
		}
	}

	public static String setProperty(String key, String value) {
		initPropertiesIfNeeded();
		return (String) properties.put(key, value);
	}

	public static String clearProperty(String key) {
		initPropertiesIfNeeded();
		return (String) properties.remove(key);
	}

	public static void setErr(TPrintStream err) {
		errCache = err;
	}

	public static void setOut(TPrintStream out) {
		outCache = out;
	}

	/** A no-op on this backend, as it already was: the JS engine owns collection. */
	public static void gc() {
	}

	public static void runFinalization() {
	}

	public static long nanoTime() {
		return (long) (Performance.now() * 1000000.0);
	}

	public static int identityHashCode(Object x) {
		return ((TObject) x).identity();
	}

	public static String lineSeparator() {
		return "\n";
	}

	public static String getenv(String name) {
		return null;
	}
}
