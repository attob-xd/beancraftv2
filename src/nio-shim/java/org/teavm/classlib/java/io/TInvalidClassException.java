package org.teavm.classlib.java.io;

/** Missing from TeaVM's class library. Java serialisation is not available; see TObjectInputStream. */
public class TInvalidClassException extends java.io.IOException {

	public TInvalidClassException() {
	}

	public TInvalidClassException(String message) {
		super(message);
	}
}
