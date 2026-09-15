package org.teavm.classlib.javax.swing;

/**
 * Missing from TeaVM's class library, and unreachable here - there is no Swing in a browser.
 * Vanilla's crash reporter builds a small window out of these, and names them in metadata
 * that TeaVM evaluates at load time, so the type has to exist. EaglercraftX shows its own
 * crash screen instead (see ClientMain.showCrashScreen).
 */
public class TJTextField {

	public TJTextField() {
		throw new UnsupportedOperationException("Swing does not exist in a browser");
	}
}
