package org.apache.logging.log4j.spi;

/**
 * The interface netty's Log4J2Logger expects its delegate to implement. EaglercraftX's
 * log4j is a much smaller thing than the real one; this exists so that the type resolves.
 * See ExtendedLoggerWrapper.
 */
public interface ExtendedLogger {

	String getName();
}
