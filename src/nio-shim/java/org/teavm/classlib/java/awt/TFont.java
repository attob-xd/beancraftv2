package org.teavm.classlib.java.awt;

/**
 * Missing from TeaVM's class library. Named by vanilla's crash window; the browser
 * rasterises text itself, through EaglercraftX's own font renderer.
 */
public class TFont {

	public static final int PLAIN = 0;
	public static final int BOLD = 1;
	public static final int ITALIC = 2;

	public TFont(String name, int style, int size) {
		throw new UnsupportedOperationException("AWT fonts do not exist in a browser");
	}
}
