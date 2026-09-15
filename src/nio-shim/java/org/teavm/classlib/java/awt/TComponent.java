package org.teavm.classlib.java.awt;

/**
 * Missing from TeaVM's class library, and unreachable: there is no AWT in a browser.
 * Vanilla's crash window and its "open a file dialog" helpers name it; EaglercraftX shows
 * its own crash screen and asks the browser for files instead. See TFont.
 */
public class TComponent {

	protected TComponent() {
		throw new UnsupportedOperationException("AWT does not exist in a browser");
	}
}
