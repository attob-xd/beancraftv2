package org.teavm.classlib.java.lang;

/**
 * Missing from TeaVM's class library. There is one thread and no grouping of it; the type
 * is named by thread-factory code that never runs. See TThreadFactory.
 */
public class TThreadGroup {

	private final String name;

	public TThreadGroup(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public int activeCount() {
		return 1;
	}
}
