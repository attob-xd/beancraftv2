package org.apache.log4j;

/**
 * log4j 1.x. netty probes for it when picking a logging backend, and finding the class is
 * how it decides. EaglercraftX has its own logger (org.apache.logging.log4j.Logger, the 2.x
 * package), so this forwards there rather than being a second implementation.
 */
public class Logger {

	private final org.apache.logging.log4j.Logger delegate;

	protected Logger(String name) {
		this.delegate = org.apache.logging.log4j.LogManager.getLogger(name);
	}

	public static Logger getLogger(String name) {
		return new Logger(name);
	}

	public static Logger getLogger(Class<?> clazz) {
		return new Logger(clazz.getSimpleName());
	}

	public boolean isDebugEnabled() {
		return delegate.isDebugEnabled();
	}

	public void debug(Object message) {
		delegate.debug(String.valueOf(message));
	}

	public void info(Object message) {
		delegate.info(String.valueOf(message));
	}

	public void warn(Object message) {
		delegate.warn(String.valueOf(message));
	}

	public void error(Object message) {
		delegate.error(String.valueOf(message));
	}
}
