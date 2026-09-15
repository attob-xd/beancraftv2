package org.slf4j;

import java.util.HashMap;
import java.util.Map;

/** See Logger. */
public final class LoggerFactory {

	private static final Map<String, Logger> LOGGERS = new HashMap<>();

	private LoggerFactory() {
	}

	public static Logger getLogger(String name) {
		Logger l = LOGGERS.get(name);
		if (l == null) {
			l = new EaglerSlf4jLogger(name);
			LOGGERS.put(name, l);
		}
		return l;
	}

	public static Logger getLogger(Class<?> clazz) {
		return getLogger(clazz.getSimpleName());
	}

	/**
	 * Real slf4j resolves a binding here by scanning the classpath. There is one logger
	 * implementation in this build and it is this class, so the factory is just a view of it.
	 */
	public static ILoggerFactory getILoggerFactory() {
		return LoggerFactory::getLogger;
	}
}
