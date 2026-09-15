package org.slf4j;

/** See LoggerFactory.getILoggerFactory. */
public interface ILoggerFactory {

	Logger getLogger(String name);
}
