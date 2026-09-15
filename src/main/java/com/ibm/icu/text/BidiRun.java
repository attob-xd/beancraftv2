package com.ibm.icu.text;

/** One directional run of text; see Bidi. */
public class BidiRun {

	private final int start;
	private final int limit;
	private final byte level;

	BidiRun(int start, int limit, byte level) {
		this.start = start;
		this.limit = limit;
		this.level = level;
	}

	public int getStart() {
		return start;
	}

	public int getLimit() {
		return limit;
	}

	public int getLength() {
		return limit - start;
	}

	public byte getEmbeddingLevel() {
		return level;
	}

	public boolean isOddRun() {
		return (level & 1) != 0;
	}

	public boolean isEvenRun() {
		return (level & 1) == 0;
	}

	public byte getDirection() {
		return (byte) (level & 1);
	}
}
