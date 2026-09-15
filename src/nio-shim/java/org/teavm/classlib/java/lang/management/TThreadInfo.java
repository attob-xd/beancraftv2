package org.teavm.classlib.java.lang.management;

/**
 * Missing from TeaVM's class library, and needed for a reason that is easy to miss: it is not
 * that MinecraftServer.dumpThreads would fail if called, it is that the lambda inside it
 * declares a ThreadInfo parameter, and TeaVM writes parameter types into class metadata that
 * the browser evaluates when the script loads. A class it cannot resolve there is a
 * ReferenceError before any game code runs - so a debug method nobody calls was stopping the
 * page from starting at all.
 *
 * There is one thread and no JMX to describe it. toString names the thread the page is
 * actually running on, which is the only true answer available and is what dumpThreads prints.
 */
public class TThreadInfo {

	private final String name;

	TThreadInfo(String name) {
		this.name = name;
	}

	public String getThreadName() {
		return name;
	}

	public long getThreadId() {
		return 0L;
	}

	/** No stack walking without JMX; an empty trace is honest, a fabricated one is not. */
	public StackTraceElement[] getStackTrace() {
		return new StackTraceElement[0];
	}

	@Override
	public String toString() {
		return "\"" + name + "\" (the only thread; a browser tab has no others)";
	}
}
