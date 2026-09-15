package org.teavm.classlib.java.lang;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Missing from TeaVM's class library. StackWalker reads the live call stack, which a
 * compiled-to-JavaScript program does not have in any form Java can inspect: TeaVM keeps
 * its own frame bookkeeping for async methods, not a walkable Java stack.
 *
 * Vanilla uses it to name the caller in a few log messages and in Util's thread dumps.
 * Walking returns nothing, so those messages lose the caller's name rather than failing.
 */
public final class TStackWalker {

	public interface StackFrame {

		String getClassName();

		String getMethodName();

		int getLineNumber();

		String getFileName();

		StackTraceElement toStackTraceElement();
	}

	public enum Option {
		RETAIN_CLASS_REFERENCE, SHOW_REFLECT_FRAMES, SHOW_HIDDEN_FRAMES
	}

	private TStackWalker() {
	}

	public static TStackWalker getInstance() {
		return new TStackWalker();
	}

	public static TStackWalker getInstance(Option option) {
		return new TStackWalker();
	}

	public static TStackWalker getInstance(java.util.Set<Option> options) {
		return new TStackWalker();
	}

	public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> function) {
		return function.apply(Stream.<StackFrame>of());
	}

	public void forEach(java.util.function.Consumer<? super StackFrame> action) {
	}

	public Class<?> getCallerClass() {
		throw new UnsupportedOperationException(
				"There is no walkable Java stack in a TeaVM-compiled page");
	}

	public List<StackFrame> frames() {
		return java.util.Collections.emptyList();
	}
}
