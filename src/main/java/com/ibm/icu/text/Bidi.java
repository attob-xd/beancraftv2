package com.ibm.icu.text;

/**
 * ICU4J's bidirectional text algorithm - the thing that puts Arabic and Hebrew runs in
 * visual order. See ArabicShaping for why icu4j itself is not on the classpath.
 *
 * This treats the whole paragraph as one left-to-right run, which is exactly right for
 * every left-to-right language and wrong for right-to-left ones: those will render in
 * logical rather than visual order. Minecraft only reaches this when the selected language
 * is marked bidirectional in pack.mcmeta.
 */
public class Bidi {

	public static final int REORDER_DEFAULT = 0;
	public static final byte LEVEL_DEFAULT_LTR = 0x7e;
	public static final byte DIRECTION_LEFT_TO_RIGHT = 0;

	private final String text;

	public Bidi(String text, int paragraphLevel) {
		this.text = text == null ? "" : text;
	}

	public Bidi(char[] text, int textStart, byte[] embeddings, int embeddingStart, int length,
			int flags) {
		this.text = new String(text, textStart, length);
	}

	public void setReorderingMode(int mode) {
	}

	public int countRuns() {
		return text.isEmpty() ? 0 : 1;
	}

	public BidiRun getVisualRun(int index) {
		return new BidiRun(0, text.length(), (byte) 0);
	}

	public String writeReordered(int options) {
		return text;
	}

	public byte getDirection() {
		return DIRECTION_LEFT_TO_RIGHT;
	}
}
