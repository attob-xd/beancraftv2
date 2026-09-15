package org.lwjgl.util.tinyfd;

import org.lwjgl.PointerBuffer;

/**
 * Replaces tinyfd, which opens native OS dialogs. A page cannot open one.
 *
 * This is not on the happy path, and that is exactly why it is worth replacing. Minecraft's
 * constructor calls tinyfd_messageBox from inside its catch block: if creating the window or
 * initialising the renderer fails, it puts the error in a native dialog. Left as the real
 * library, that call would fail natively on top of the failure it was trying to report, and
 * the second error is the one that would surface - hiding the first.
 *
 * So the message box logs and returns "OK". The crash report the client builds anyway is
 * where the error is actually readable, and EaglercraftX renders that into the page.
 */
public final class TinyFileDialogs {

	private TinyFileDialogs() {
	}

	public static boolean tinyfd_messageBox(CharSequence title, CharSequence message,
			CharSequence dialogType, CharSequence iconType, boolean defaultButton) {
		// System.err rather than a logger: this is called while reporting a failure, and the
		// logging stack is one of the things that may not be standing at that point.
		System.err.println("[" + title + "] " + message);
		return true;
	}

	/**
	 * Vanilla offers this on the world-creation screen, to import a settings file. There is
	 * no file picker to open; null is "the user cancelled", which the caller handles.
	 */
	public static String tinyfd_openFileDialog(CharSequence title, CharSequence defaultPath,
			PointerBuffer filterPatterns, CharSequence filterDescription, boolean allowMultiple) {
		return null;
	}

	public static String tinyfd_saveFileDialog(CharSequence title, CharSequence defaultPath,
			PointerBuffer filterPatterns, CharSequence filterDescription) {
		return null;
	}

	public static String tinyfd_selectFolderDialog(CharSequence title, CharSequence defaultPath) {
		return null;
	}
}
