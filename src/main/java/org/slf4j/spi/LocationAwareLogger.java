package org.slf4j.spi;

import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * Lets a logging bridge report the caller's location rather than its own. There is no
 * walkable stack here (see java.lang.StackWalker), so the fqcn is ignored and the message
 * is logged normally. netty checks for this interface when it wraps a logger.
 */
public interface LocationAwareLogger extends Logger {

	int TRACE_INT = 0;
	int DEBUG_INT = 10;
	int INFO_INT = 20;
	int WARN_INT = 30;
	int ERROR_INT = 40;

	void log(Marker marker, String fqcn, int level, String message, Object[] argArray,
			Throwable t);
}
