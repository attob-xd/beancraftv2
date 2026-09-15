package org.lwjgl.glfw;

import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;

import net.lax1dude.eaglercraft.KeyboardConstants;
import net.lax1dude.eaglercraft.internal.PlatformApplication;
import net.lax1dude.eaglercraft.internal.PlatformInput;

/**
 * Replaces GLFW, the window and input library vanilla talks to on a desktop.
 *
 * This is the seam the client-side port turns on. 1.18.2 routes every key press, mouse move
 * and scroll through GLFW callbacks that MouseHandler and KeyboardHandler register at
 * startup; EaglercraftX produces the same events, but as a poll queue in the LWJGL 2 style
 * that 1.12.2 used. Translating here rather than rewriting MouseHandler and KeyboardHandler
 * means vanilla's input logic - key repeat, modifiers, the debug-key combinations, the whole
 * of KeyMapping - runs unmodified, which is the point of porting the fork instead of
 * rebuilding it.
 *
 * glfwPollEvents() is where that happens: it drains PlatformInput's queues and calls the
 * registered callbacks, so an event that entered as a browser KeyboardEvent leaves as the
 * GLFWKeyCallbackI invocation vanilla is waiting for. Two conversions are needed and both
 * are real, not cosmetic:
 *
 *   - Key codes. EaglercraftX carries LWJGL 2 scancodes and GLFW's are different numbers
 *     entirely, so every code goes through KeyboardConstants, which already holds the table
 *     in both directions.
 *   - Mouse Y. PlatformInput measures from the bottom of the canvas, GLFW from the top, so
 *     the axis is flipped. Getting this wrong does not fail loudly - it inverts the mouse -
 *     so it is done in one place, glfwY().
 *
 * Everything a page cannot do is answered honestly rather than faked. There are no monitors
 * to enumerate, so glfwGetMonitors returns an empty buffer instead of a made-up one and
 * vanilla's ScreenManager simply finds none; there is no video mode to choose; the window
 * handle is 0 and every method ignores it, because there is one canvas.
 */
public final class GLFW {

	private GLFW() {
	}

	/**
	 * LWJGL keeps the addresses of GLFW's entry points here, and its own Callbacks class
	 * names the type. There are no entry points to hold - every method above is Java - so
	 * this is empty. It exists because a class TeaVM cannot resolve is a ReferenceError when
	 * the page loads, while a field it cannot find is not.
	 */
	public static final class Functions {

		private Functions() {
		}
	}

	/*
	 * The constants vanilla was compiled against are already inlined into its class files,
	 * so these exist for this project's own code and for readability at the call sites
	 * below. The values are GLFW's, not invented.
	 */

	public static final int GLFW_RELEASE = 0;
	public static final int GLFW_PRESS = 1;
	public static final int GLFW_REPEAT = 2;

	public static final int GLFW_KEY_UNKNOWN = -1;

	public static final int GLFW_MOD_SHIFT = 0x1;
	public static final int GLFW_MOD_CONTROL = 0x2;
	public static final int GLFW_MOD_ALT = 0x4;
	public static final int GLFW_MOD_SUPER = 0x8;

	public static final int GLFW_MOUSE_BUTTON_LEFT = 0;
	public static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
	public static final int GLFW_MOUSE_BUTTON_MIDDLE = 2;

	public static final int GLFW_CURSOR = 0x00033001;
	public static final int GLFW_RAW_MOUSE_MOTION = 0x00033005;
	public static final int GLFW_CURSOR_NORMAL = 0x00034001;
	public static final int GLFW_CURSOR_HIDDEN = 0x00034002;
	public static final int GLFW_CURSOR_DISABLED = 0x00034003;

	/** EaglercraftX's key codes, needed to read modifier state out of PlatformInput. */
	private static final int EAG_LSHIFT = KeyboardConstants.KEY_LSHIFT;
	private static final int EAG_RSHIFT = KeyboardConstants.KEY_RSHIFT;
	private static final int EAG_LCONTROL = KeyboardConstants.KEY_LCONTROL;
	private static final int EAG_RCONTROL = KeyboardConstants.KEY_RCONTROL;
	private static final int EAG_LMENU = KeyboardConstants.KEY_LMENU;
	private static final int EAG_RMENU = KeyboardConstants.KEY_RMENU;
	private static final int EAG_LMETA = KeyboardConstants.KEY_LMETA;
	private static final int EAG_RMETA = KeyboardConstants.KEY_RMETA;

	private static GLFWKeyCallbackI keyCallback;
	private static GLFWCharModsCallbackI charModsCallback;
	private static GLFWCursorPosCallbackI cursorPosCallback;
	private static GLFWMouseButtonCallbackI mouseButtonCallback;
	private static GLFWScrollCallbackI scrollCallback;
	private static GLFWDropCallbackI dropCallback;
	private static GLFWErrorCallbackI errorCallback;
	private static GLFWMonitorCallbackI monitorCallback;

	private static long startNanos;
	private static int lastCursorX = -1;
	private static int lastCursorY = -1;

	public static boolean glfwInit() {
		startNanos = System.nanoTime();
		return true;
	}

	public static void glfwTerminate() {
	}

	/** Seconds since glfwInit, which is what vanilla's frame timing expects. */
	public static double glfwGetTime() {
		return (System.nanoTime() - startNanos) / 1.0E9D;
	}

	/** There is one canvas and its handle is 0; see the class comment. */
	public static long glfwGetCurrentContext() {
		return 0L;
	}

	public static boolean glfwWindowShouldClose(long window) {
		return PlatformInput.isCloseRequested();
	}

	/**
	 * The browser presents the finished frame itself when the callback that drew it
	 * returns, so there is no buffer to swap. PlatformInput.update() is the equivalent
	 * step and Window.updateDisplay() already calls it; doing it here as well would count
	 * two frames per frame in the vsync limiter.
	 */
	public static void glfwSwapBuffers(long window) {
	}

	public static void glfwSwapInterval(int interval) {
		PlatformInput.setVSync(interval > 0);
	}

	// --- input ---

	private static int glfwY(int eaglerY) {
		return Math.max(0, PlatformInput.getWindowHeight() - 1 - eaglerY);
	}

	private static int mods() {
		int m = 0;
		if (PlatformInput.keyboardIsKeyDown(EAG_LSHIFT) || PlatformInput.keyboardIsKeyDown(EAG_RSHIFT)) {
			m |= GLFW_MOD_SHIFT;
		}
		if (PlatformInput.keyboardIsKeyDown(EAG_LCONTROL) || PlatformInput.keyboardIsKeyDown(EAG_RCONTROL)) {
			m |= GLFW_MOD_CONTROL;
		}
		if (PlatformInput.keyboardIsKeyDown(EAG_LMENU) || PlatformInput.keyboardIsKeyDown(EAG_RMENU)) {
			m |= GLFW_MOD_ALT;
		}
		if (PlatformInput.keyboardIsKeyDown(EAG_LMETA) || PlatformInput.keyboardIsKeyDown(EAG_RMETA)) {
			m |= GLFW_MOD_SUPER;
		}
		return m;
	}

	/**
	 * Drains EaglercraftX's event queues into vanilla's callbacks. This is the whole
	 * translation; see the class comment.
	 */
	public static void glfwPollEvents() {
		while (PlatformInput.keyboardNext()) {
			int eagKey = PlatformInput.keyboardGetEventKey();
			boolean down = PlatformInput.keyboardGetEventKeyState();
			int m = mods();
			if (eagKey > 0 && keyCallback != null) {
				int key = KeyboardConstants.getGLFWKeyFromEagler(eagKey);
				if (key != 0) {
					int action = down
							? (PlatformInput.keyboardIsRepeatEvent() ? GLFW_REPEAT : GLFW_PRESS)
							: GLFW_RELEASE;
					// GLFW's scancode is platform-specific, and vanilla only falls back to it
					// when the key itself is unknown - which cannot happen once the table has
					// mapped it - so 0 is passed rather than a fabricated code.
					keyCallback.invoke(0L, key, 0, action, m);
				}
			}
			// GLFW raises a separate character event and vanilla's KeyboardHandler needs it
			// for text fields. EaglercraftX carries the character on the key event instead,
			// so it is split back out here. Control characters are not text, and GLFW does
			// not report them either.
			if (down && charModsCallback != null) {
				char c = PlatformInput.keyboardGetEventCharacter();
				if (c >= 32 && c != 127) {
					charModsCallback.invoke(0L, c, m);
				}
			}
		}
		while (PlatformInput.mouseNext()) {
			int x = PlatformInput.mouseGetEventX();
			int y = glfwY(PlatformInput.mouseGetEventY());
			if ((x != lastCursorX || y != lastCursorY) && cursorPosCallback != null) {
				lastCursorX = x;
				lastCursorY = y;
				cursorPosCallback.invoke(0L, x, y);
			}
			int wheel = PlatformInput.mouseGetEventDWheel();
			if (wheel != 0) {
				if (scrollCallback != null) {
					scrollCallback.invoke(0L, 0.0D, wheel);
				}
				continue;
			}
			int button = PlatformInput.mouseGetEventButton();
			if (button >= 0 && mouseButtonCallback != null) {
				mouseButtonCallback.invoke(0L, button,
						PlatformInput.mouseGetEventButtonState() ? GLFW_PRESS : GLFW_RELEASE,
						mods());
			}
		}
	}

	/**
	 * Vanilla calls this to idle while a resource reload runs. There is nothing to block on
	 * here - the page's own event loop is what delivers events, and stopping inside this
	 * method would stop it - so this only drains what has already arrived.
	 */
	public static void glfwWaitEventsTimeout(double timeout) {
		glfwPollEvents();
	}

	public static int glfwGetKey(long window, int key) {
		int eag = KeyboardConstants.getEaglerKeyFromGLFW(key);
		return eag != 0 && PlatformInput.keyboardIsKeyDown(eag) ? GLFW_PRESS : GLFW_RELEASE;
	}

	/**
	 * GLFW returns the character the key produces on the current layout. EaglercraftX holds
	 * that table too, keyed by its own code.
	 */
	public static String glfwGetKeyName(int key, int scancode) {
		int eag = KeyboardConstants.getEaglerKeyFromGLFW(key);
		if (eag == 0) {
			return null;
		}
		char c = KeyboardConstants.getKeyCharFromEagler(eag);
		return c == 0 ? null : String.valueOf(c);
	}

	public static void glfwSetCursorPos(long window, double x, double y) {
		PlatformInput.mouseSetCursorPosition((int) x, PlatformInput.getWindowHeight() - 1 - (int) y);
	}

	/**
	 * Vanilla hides and captures the cursor for mouselook through this. The browser's
	 * equivalent is pointer lock, which is what mouseSetGrabbed drives - and which the
	 * browser may refuse outside a user gesture, so the request can silently not take
	 * effect. That is the browser's rule, not a gap here.
	 */
	public static void glfwSetInputMode(long window, int mode, int value) {
		if (mode == GLFW_CURSOR) {
			PlatformInput.mouseSetGrabbed(value == GLFW_CURSOR_DISABLED);
		}
		// GLFW_RAW_MOUSE_MOTION has no browser equivalent; pointer lock already reports
		// unaccelerated movement wherever the platform provides it.
	}

	public static String glfwGetClipboardString(long window) {
		return PlatformApplication.getClipboard();
	}

	public static void glfwSetClipboardString(long window, ByteBuffer string) {
		if (string == null) {
			return;
		}
		StringBuilder sb = new StringBuilder(string.remaining());
		for (int i = string.position(); i < string.limit(); ++i) {
			byte b = string.get(i);
			if (b == 0) {
				break;
			}
			sb.append((char) (b & 0xFF));
		}
		PlatformApplication.setClipboard(sb.toString());
	}

	public static void glfwSetClipboardString(long window, CharSequence string) {
		PlatformApplication.setClipboard(string == null ? "" : string.toString());
	}

	// --- callback registration ---
	//
	// GLFW hands back the callback it replaced so the caller can free it. Nothing is
	// allocated natively here and vanilla discards the result, so these return null.

	public static GLFWKeyCallback glfwSetKeyCallback(long window, GLFWKeyCallbackI cb) {
		keyCallback = cb;
		return null;
	}

	public static GLFWCharModsCallback glfwSetCharModsCallback(long window, GLFWCharModsCallbackI cb) {
		charModsCallback = cb;
		return null;
	}

	public static GLFWCursorPosCallback glfwSetCursorPosCallback(long window, GLFWCursorPosCallbackI cb) {
		cursorPosCallback = cb;
		return null;
	}

	public static GLFWMouseButtonCallback glfwSetMouseButtonCallback(long window,
			GLFWMouseButtonCallbackI cb) {
		mouseButtonCallback = cb;
		return null;
	}

	public static GLFWScrollCallback glfwSetScrollCallback(long window, GLFWScrollCallbackI cb) {
		scrollCallback = cb;
		return null;
	}

	/** Files cannot be dropped onto the canvas, so this callback is never invoked. */
	public static GLFWDropCallback glfwSetDropCallback(long window, GLFWDropCallbackI cb) {
		dropCallback = cb;
		return null;
	}

	public static GLFWErrorCallback glfwSetErrorCallback(GLFWErrorCallbackI cb) {
		errorCallback = cb;
		return null;
	}

	public static GLFWMonitorCallback glfwSetMonitorCallback(GLFWMonitorCallbackI cb) {
		monitorCallback = cb;
		return null;
	}

	// --- monitors and video modes ---
	//
	// A page cannot enumerate the user's displays and should not be able to. Vanilla's
	// ScreenManager loops over whatever comes back and finds nothing, which leaves the
	// fullscreen path to Window - and Window drives the browser's Fullscreen API instead.

	public static PointerBuffer glfwGetMonitors() {
		return PointerBuffer.allocateDirect(0);
	}

	public static long glfwGetPrimaryMonitor() {
		return 0L;
	}

	public static long glfwGetWindowMonitor(long window) {
		return 0L;
	}

	public static void glfwGetMonitorPos(long monitor, int[] x, int[] y) {
		if (x != null && x.length > 0) {
			x[0] = 0;
		}
		if (y != null && y.length > 0) {
			y[0] = 0;
		}
	}

	/**
	 * Both mode queries are unreachable, because glfwGetMonitors returns nothing and vanilla
	 * only asks a Monitor it was given. Returning null rather than an empty GLFWVidMode is
	 * deliberate: that struct is a view onto native memory, and constructing a fake one would
	 * mean replacing LWJGL's whole Struct/CustomBuffer hierarchy - which the rest of the jar
	 * still extends - to describe a display this build cannot see.
	 */
	public static GLFWVidMode glfwGetVideoMode(long monitor) {
		return null;
	}

	public static GLFWVidMode.Buffer glfwGetVideoModes(long monitor) {
		return null;
	}
}
