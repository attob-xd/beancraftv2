package org.slf4j;

import net.lax1dude.eaglercraft.EagRuntime;

/**
 * Bridges org.slf4j.Logger onto EaglercraftX's own logger; see Logger for why this exists
 * instead of the slf4j-api jar.
 *
 * The two share the '{}' placeholder syntax, so a call forwards directly. Where slf4j
 * takes a trailing Throwable, EaglercraftX's logger has no matching overload, so the
 * message and the stack trace are logged as two lines rather than one.
 */
final class EaglerSlf4jLogger implements Logger {

	private final org.apache.logging.log4j.Logger delegate;
	private final String name;

	EaglerSlf4jLogger(String name) {
		this.name = name;
		this.delegate = org.apache.logging.log4j.LogManager.getLogger(name);
	}

	/*
	 * slf4j's trailing-throwable convention, which this bridge used to drop on the floor.
	 *
	 * LOGGER.error("Couldn't load {} metadata", name, exception) passes two arguments for one
	 * placeholder, and slf4j treats the extra trailing Throwable as the exception rather than
	 * as a value to substitute. Forwarding it as an ordinary argument printed its toString and
	 * threw the stack trace away - so every exception vanilla catches and logs, which is most
	 * of its error handling, arrived with no indication of where it came from. That cost real
	 * debugging time: a JSON parse failure showed up as one line with no cause.
	 */
	private static int placeholders(String format) {
		int count = 0;
		int i = 0;
		while (format != null && (i = format.indexOf("{}", i)) >= 0) {
			++count;
			i += 2;
		}
		return count;
	}

	private static Throwable trailingThrowable(String format, Object[] args) {
		if (args == null || args.length == 0) {
			return null;
		}
		Object last = args[args.length - 1];
		if (!(last instanceof Throwable)) {
			return null;
		}
		return placeholders(format) < args.length ? (Throwable) last : null;
	}

	private static Object[] withoutLast(Object[] args) {
		Object[] out = new Object[args.length - 1];
		System.arraycopy(args, 0, out, 0, out.length);
		return out;
	}

	private void logArgs(int level, String format, Object[] args) {
		Throwable t = trailingThrowable(format, args);
		Object[] values = t == null ? args : withoutLast(args);
		switch (level) {
			case 0: delegate.trace(format, values); break;
			case 1: delegate.debug(format, values); break;
			case 2: delegate.info(format, values); break;
			case 3: delegate.warn(format, values); break;
			default: delegate.error(format, values); break;
		}
		if (t != null) {
			String trace = EagRuntime.getStackTrace(t);
			switch (level) {
				case 0: delegate.trace(trace); break;
				case 1: delegate.debug(trace); break;
				case 2: delegate.info(trace); break;
				case 3: delegate.warn(trace); break;
				default: delegate.error(trace); break;
			}
		}
	}

	@Override
	public String getName() {
		return name;
	}

	private static String withMarker(Marker marker, String msg) {
		return marker == null ? msg : "[" + marker.getName() + "] " + msg;
	}

	/**
	 * Reported as disabled, deliberately, even when the delegate has debug on.
	 *
	 * Vanilla uses these two to guard work that is only safe on a JVM. The one that matters
	 * is SynchedEntityData.defineId: with debug enabled it reads
	 * Thread.currentThread().getStackTrace()[2] and hands the class name to Class.forName, to
	 * check the caller is the class it claims to be. TeaVM has no call stack to walk -
	 * getStackTrace() returns a shorter array, element 2 is undefined, and the client died
	 * with a raw TypeError inside LivingEntity's static initialiser, before drawing anything.
	 *
	 * A shipped Minecraft client runs at INFO and never takes that branch either, so this
	 * matches the real thing rather than working around it. debug() and trace() below still
	 * forward, so an unguarded call is not silently dropped; what is switched off is code
	 * that asks first whether debugging is on - which is precisely the code that assumes
	 * facilities this build does not have.
	 */
	@Override
	public boolean isTraceEnabled() {
		return false;
	}

	@Override
	public void trace(String msg) {
		delegate.trace(msg);
	}

	@Override
	public void trace(String format, Object arg) {
		logArgs(0, format, new Object[] { arg });
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		logArgs(0, format, new Object[] { arg1, arg2 });
	}

	@Override
	public void trace(String format, Object... args) {
		logArgs(0, format, args);
	}

	@Override
	public void trace(String msg, Throwable t) {
		delegate.trace(msg);
		delegate.trace(EagRuntime.getStackTrace(t));
	}

	/** See isTraceEnabled. */
	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public void debug(String msg) {
		delegate.debug(msg);
	}

	@Override
	public void debug(String format, Object arg) {
		logArgs(1, format, new Object[] { arg });
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		logArgs(1, format, new Object[] { arg1, arg2 });
	}

	@Override
	public void debug(String format, Object... args) {
		logArgs(1, format, args);
	}

	@Override
	public void debug(String msg, Throwable t) {
		delegate.debug(msg);
		delegate.debug(EagRuntime.getStackTrace(t));
	}

	@Override
	public void debug(Marker marker, String msg) {
		delegate.debug(withMarker(marker, msg));
	}

	@Override
	public void debug(Marker marker, String format, Object arg) {
		delegate.debug(withMarker(marker, format), arg);
	}

	@Override
	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		delegate.debug(withMarker(marker, format), arg1, arg2);
	}

	@Override
	public void debug(Marker marker, String format, Object... args) {
		delegate.debug(withMarker(marker, format), args);
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public void info(String msg) {
		delegate.info(msg);
	}

	@Override
	public void info(String format, Object arg) {
		logArgs(2, format, new Object[] { arg });
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		logArgs(2, format, new Object[] { arg1, arg2 });
	}

	@Override
	public void info(String format, Object... args) {
		logArgs(2, format, args);
	}

	@Override
	public void info(String msg, Throwable t) {
		delegate.info(msg);
		delegate.info(EagRuntime.getStackTrace(t));
	}

	@Override
	public void info(Marker marker, String msg) {
		delegate.info(withMarker(marker, msg));
	}

	@Override
	public boolean isWarnEnabled() {
		return true;
	}

	@Override
	public void warn(String msg) {
		delegate.warn(msg);
	}

	@Override
	public void warn(String format, Object arg) {
		logArgs(3, format, new Object[] { arg });
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		logArgs(3, format, new Object[] { arg1, arg2 });
	}

	@Override
	public void warn(String format, Object... args) {
		logArgs(3, format, args);
	}

	@Override
	public void warn(String msg, Throwable t) {
		delegate.warn(msg);
		delegate.warn(EagRuntime.getStackTrace(t));
	}

	@Override
	public void warn(Marker marker, String format, Object arg) {
		delegate.warn(withMarker(marker, format), arg);
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public void error(String msg) {
		delegate.error(msg);
	}

	@Override
	public void error(String format, Object arg) {
		logArgs(4, format, new Object[] { arg });
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		logArgs(4, format, new Object[] { arg1, arg2 });
	}

	@Override
	public void error(String format, Object... args) {
		logArgs(4, format, args);
	}

	@Override
	public void error(String msg, Throwable t) {
		delegate.error(msg);
		delegate.error(EagRuntime.getStackTrace(t));
	}

	@Override
	public void error(Marker marker, String msg) {
		delegate.error(withMarker(marker, msg));
	}

	@Override
	public void error(Marker marker, String format, Object arg) {
		delegate.error(withMarker(marker, format), arg);
	}

	@Override
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		delegate.error(withMarker(marker, format), arg1, arg2);
	}

	@Override
	public void error(Marker marker, String msg, Throwable t) {
		delegate.error(withMarker(marker, msg));
		delegate.error(EagRuntime.getStackTrace(t));
	}
}
