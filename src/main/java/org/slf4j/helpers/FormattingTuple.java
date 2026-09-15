package org.slf4j.helpers;

/** See MessageFormatter. */
public class FormattingTuple {

	private final String message;
	private final Object[] argArray;
	private final Throwable throwable;

	public FormattingTuple(String message, Object[] argArray, Throwable throwable) {
		this.message = message;
		this.argArray = argArray;
		this.throwable = throwable;
	}

	public String getMessage() {
		return message;
	}

	public Object[] getArgArray() {
		return argArray;
	}

	public Throwable getThrowable() {
		return throwable;
	}
}
