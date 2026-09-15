package com.mojang.logging;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Replaces Mojang's logging library, which every one of 1.18.2's 353 logging classes calls
 * to get its logger. The real one binds slf4j to log4j-core and installs a queueing
 * appender for the launcher's log window; none of that has a place in a browser tab, and
 * log4j-core is precisely the jar whose Class.newInstance() made TeaVM treat the entire
 * classpath as reachable. See org.slf4j.Logger.
 *
 * Vanilla calls getLogger() from a static initialiser, so the caller's own class name is
 * not available the way the real implementation gets it (a stack walk, which TeaVM does
 * not support). Loggers are named for the caller only where it passes a name; otherwise
 * they share one channel, which the page console renders identically.
 */
public class LogUtils {

	public static final String FATAL_MARKER_ID = "FATAL";
	public static final Marker FATAL_MARKER = MarkerFactory.getMarker(FATAL_MARKER_ID);

	public static Logger getLogger() {
		return LoggerFactory.getLogger("Minecraft");
	}

	public static boolean isLoggerActive() {
		return true;
	}

	/**
	 * Wraps a supplier so the message is only built if the line is actually printed. The
	 * real one returns an object whose toString() calls the supplier; so does this.
	 */
	public static Object defer(Supplier<Object> supplier) {
		return new Object() {
			@Override
			public String toString() {
				return String.valueOf(supplier.get());
			}
		};
	}

	public static void configureRootLoggingLevel(org.slf4j.event.Level level) {
		// The page console has no level filter of its own; EaglercraftX's LogManager keeps
		// the only threshold, and it is set from the client's own options.
	}
}
