package org.slf4j.helpers;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real slf4j falls back to this when no binding is found. There is always a binding here -
 * EaglercraftX's own logger - so this hands back the same loggers rather than discarding
 * the output. See org.slf4j.Logger.
 */
public class NOPLoggerFactory implements ILoggerFactory {

	@Override
	public Logger getLogger(String name) {
		return LoggerFactory.getLogger(name);
	}
}
