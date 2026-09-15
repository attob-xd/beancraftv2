package org.slf4j.helpers;

/**
 * slf4j's '{}' substitution. EaglercraftX's own logger already does this (see
 * org.apache.logging.log4j.Logger.formatParams), but netty calls the helper directly, so it
 * has to exist. See org.slf4j.Logger for why the real slf4j jar is not on the classpath.
 */
public final class MessageFormatter {

	private MessageFormatter() {
	}

	public static FormattingTuple format(String pattern, Object arg) {
		return arrayFormat(pattern, new Object[] { arg });
	}

	public static FormattingTuple format(String pattern, Object arg1, Object arg2) {
		return arrayFormat(pattern, new Object[] { arg1, arg2 });
	}

	public static FormattingTuple arrayFormat(String pattern, Object[] args) {
		if (pattern == null) {
			return new FormattingTuple(null, args, null);
		}
		if (args == null || args.length == 0) {
			return new FormattingTuple(pattern, args, null);
		}
		// A trailing Throwable is the message's cause, not a placeholder value - that is
		// slf4j's rule, and netty relies on it.
		Throwable cause = args[args.length - 1] instanceof Throwable
				? (Throwable) args[args.length - 1]
				: null;
		StringBuilder out = new StringBuilder();
		int arg = 0;
		int i = 0;
		while (i < pattern.length()) {
			int at = pattern.indexOf("{}", i);
			if (at < 0 || arg >= args.length) {
				out.append(pattern, i, pattern.length());
				break;
			}
			out.append(pattern, i, at);
			out.append(args[arg++]);
			i = at + 2;
		}
		return new FormattingTuple(out.toString(), args, cause);
	}
}
