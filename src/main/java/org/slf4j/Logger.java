package org.slf4j;

/**
 * 1.18.2 does not log through log4j. Every one of its 353 logging classes goes through
 * com.mojang.logging.LogUtils.getLogger(), which hands back an org.slf4j.Logger - vanilla
 * references org.apache.logging.log4j.LogManager in exactly zero classes.
 *
 * EaglercraftX ships its own tiny log4j (org.apache.logging.log4j.Logger, which writes to
 * the page console and can be piped back from the singleplayer worker). This interface is
 * the same idea one layer up: the slf4j surface vanilla actually calls - measured with
 * tools/scan_slf4j.py - bridged onto that logger by EaglerSlf4jLogger. The real slf4j-api
 * jar is not on the classpath, because its binding machinery drags log4j-core, and with it
 * a Class.newInstance() that TeaVM turns into "every class is reachable".
 *
 * The '{}' placeholder syntax is identical in both, so the bridge is a straight forward.
 */
public interface Logger {

	String getName();

	boolean isTraceEnabled();

	void trace(String msg);

	void trace(String format, Object arg);

	void trace(String format, Object arg1, Object arg2);

	void trace(String format, Object... args);

	void trace(String msg, Throwable t);

	boolean isDebugEnabled();

	void debug(String msg);

	void debug(String format, Object arg);

	void debug(String format, Object arg1, Object arg2);

	void debug(String format, Object... args);

	void debug(String msg, Throwable t);

	void debug(Marker marker, String msg);

	void debug(Marker marker, String format, Object arg);

	void debug(Marker marker, String format, Object arg1, Object arg2);

	void debug(Marker marker, String format, Object... args);

	boolean isInfoEnabled();

	void info(String msg);

	void info(String format, Object arg);

	void info(String format, Object arg1, Object arg2);

	void info(String format, Object... args);

	void info(String msg, Throwable t);

	void info(Marker marker, String msg);

	boolean isWarnEnabled();

	void warn(String msg);

	void warn(String format, Object arg);

	void warn(String format, Object arg1, Object arg2);

	void warn(String format, Object... args);

	void warn(String msg, Throwable t);

	void warn(Marker marker, String format, Object arg);

	boolean isErrorEnabled();

	void error(String msg);

	void error(String format, Object arg);

	void error(String format, Object arg1, Object arg2);

	void error(String format, Object... args);

	void error(String msg, Throwable t);

	void error(Marker marker, String msg);

	void error(Marker marker, String format, Object arg);

	void error(Marker marker, String format, Object arg1, Object arg2);

	void error(Marker marker, String msg, Throwable t);
}
