package org.apache.logging.log4j.spi;

import org.apache.logging.log4j.Logger;

/**
 * Real log4j is not on this classpath - EaglercraftX ships its own tiny
 * org.apache.logging.log4j, and the jar was dropped because AbstractLogger's
 * Class.newInstance() made TeaVM treat every class as instantiable (see build.gradle).
 *
 * netty's Log4J2Logger still names this as its supertype, though, and TeaVM evaluates that
 * at load time, so the type has to exist. It wraps EaglercraftX's logger, which is where
 * netty's output should go anyway.
 */
public class ExtendedLoggerWrapper {

	protected final Logger logger;
	protected final String name;

	public ExtendedLoggerWrapper(Logger logger, String name, Object messageFactory) {
		this.logger = logger;
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
