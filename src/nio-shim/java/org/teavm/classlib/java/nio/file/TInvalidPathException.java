package org.teavm.classlib.java.nio.file;

public class TInvalidPathException extends IllegalArgumentException {

	private final String input;
	private final int index;

	public TInvalidPathException(String input, String reason) {
		this(input, reason, -1);
	}

	public TInvalidPathException(String input, String reason, int index) {
		super(reason + ": " + input);
		this.input = input;
		this.index = index;
	}

	public String getInput() {
		return input;
	}

	public int getIndex() {
		return index;
	}

	public String getReason() {
		return super.getMessage();
	}
}
