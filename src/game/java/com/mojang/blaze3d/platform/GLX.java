package com.mojang.blaze3d.platform;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.lax1dude.eaglercraft.internal.PlatformOpenGL;
import net.lax1dude.eaglercraft.opengl.RealOpenGLEnums;

/**
 * Replaces blaze3d's bootstrap for the native graphics stack.
 *
 * On a desktop this initialises GLFW, asks OSHI for the CPU model and installs an OpenGL
 * debug callback. None of the three exists in a page, and this class is exactly where they
 * are reached from: RenderSystem calls _initGlfw() and _init() during Minecraft's
 * constructor, so leaving it as vanilla is what made the client die before it drew anything.
 *
 * Replacing GLX rather than RenderSystem is deliberate. RenderSystem is the whole render
 * state machine and this port wants it unmodified; GLX is the thin edge where it touches the
 * platform, so cutting here keeps GlDebug (an OpenGL debug-output callback WebGL has no
 * equivalent for) and OSHI (which reads the host CPU, which a page must not do) off the
 * reachable path entirely, instead of shimming them.
 *
 * The context itself is already up by the time this runs - EaglercraftX creates the canvas
 * and its WebGL context during platform init, well before Minecraft is constructed - so
 * there is nothing left for _init to do but say what it found.
 */
public class GLX {

	private static final Logger LOGGER = LogUtils.getLogger();

	private static String cpuInfo;

	/**
	 * Vanilla returns GLFW's clock here and Minecraft assigns it to Util.timeSource, so it
	 * becomes the game's clock. System.nanoTime is what Util defaults to anyway, and under
	 * TeaVM it reads the same monotonic browser clock EaglercraftX uses.
	 */
	public static LongSupplier _initGlfw() {
		return System::nanoTime;
	}

	/**
	 * The context exists already; see the class comment. The verbosity argument selects an
	 * OpenGL debug-output level, which WebGL does not expose - errors surface through
	 * getError and the browser console instead - so it is logged rather than silently
	 * dropped, and the caller can see that asking for it did nothing.
	 */
	public static void _init(int debugVerbosity, boolean synchronous) {
		if (debugVerbosity > 0) {
			LOGGER.info("Ignoring request for OpenGL debug output (verbosity {}): WebGL has no "
					+ "debug callback; errors are reported through the browser console",
					debugVerbosity);
		}
	}

	/**
	 * A page is not allowed to know the host CPU, and should not be. Vanilla builds this
	 * from OSHI for the F3 screen and crash reports; saying so is more useful there than a
	 * guess would be.
	 */
	public static String _getCpuInfo() {
		if (cpuInfo == null) {
			cpuInfo = "<not available in a browser>";
		}
		return cpuInfo;
	}

	public static String getOpenGLVersionString() {
		return PlatformOpenGL._wglGetString(RealOpenGLEnums.GL_VERSION);
	}

	public static String _getLWJGLVersion() {
		return org.lwjgl.Version.getVersion();
	}

	/**
	 * Minecraft's constructor installs an error callback through RenderSystem, which lands
	 * here. On a desktop GLFW reports window and input errors through it; the browser has no
	 * equivalent channel, so the callback is accepted and never invoked - WebGL and DOM
	 * errors surface through the console and through this project's own logging instead.
	 *
	 * Leaving this method out of the replacement was a mistake that cost a build: the call
	 * is on the boot path, so an absent GLX._setGlfwErrorCallback is a NoSuchMethodError
	 * inside Minecraft's constructor rather than an unused stub.
	 */
	public static void _setGlfwErrorCallback(org.lwjgl.glfw.GLFWErrorCallbackI callback) {
		org.lwjgl.glfw.GLFW.glfwSetErrorCallback(callback);
	}

	public static int _getRefreshRate(Window window) {
		return window.getRefreshRate();
	}

	public static boolean _shouldClose(Window window) {
		return window.shouldClose();
	}

	/**
	 * Vanilla draws three coloured lines along the axes for the debug view, in immediate
	 * mode - glBegin/glEnd - which is not in OpenGL ES and so not in WebGL. It is a debug
	 * overlay and nothing depends on it, so it is left undrawn rather than reimplemented.
	 */
	public static void _renderCrosshair(int length, boolean drawX, boolean drawY, boolean drawZ) {
	}

	/*
	 * Two plain helpers vanilla keeps here; no platform involvement, kept verbatim so the
	 * callers (GlDebug, DebugMemoryUntracker) behave identically.
	 */

	public static <T> T make(Supplier<T> supplier) {
		return supplier.get();
	}

	public static <T> T make(T object, Consumer<T> consumer) {
		consumer.accept(object);
		return object;
	}
}
