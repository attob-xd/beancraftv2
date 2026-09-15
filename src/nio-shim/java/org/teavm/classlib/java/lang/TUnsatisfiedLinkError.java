package org.teavm.classlib.java.lang;

/** Missing from TeaVM's class library. A page cannot load a native library; nothing here has one to load. */
public class TUnsatisfiedLinkError extends Error {

	public TUnsatisfiedLinkError() {
	}

	public TUnsatisfiedLinkError(String message) {
		super(message);
	}
}
