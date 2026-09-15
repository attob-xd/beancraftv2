package com.mojang.blaze3d.platform;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.lax1dude.eaglercraft.internal.PlatformInput;
import net.lax1dude.eaglercraft.internal.PlatformRuntime;

/**
 * Replaces blaze3d's window, which is a GLFW window on a desktop.
 *
 * There is no window here - there is a canvas in a page, whose size is decided by the
 * document and by the user's browser chrome. So this reads its dimensions from
 * EaglercraftX's PlatformInput, which is already tracking the canvas and its visual
 * viewport, and reports them as the framebuffer size. Everything a desktop window can do
 * that a canvas cannot is answered honestly below rather than pretended at: the page
 * cannot set its own icon or title bar, cannot choose a video mode, and cannot decide when
 * it closes.
 *
 * Fullscreen is the one that does work: the browser's Fullscreen API is what
 * PlatformInput.toggleFullscreen drives, and it is also what EaglercraftX's own options
 * screen uses, so the two agree.
 *
 * getWindow() returns 0. On a desktop it is the GLFW window handle, and the callers that
 * pass it on - MouseHandler, KeyboardHandler - are replaced by EaglercraftX's own input
 * handling, which never looks at it.
 */
public final class Window implements AutoCloseable {

	private static final Logger LOGGER = LogUtils.getLogger();

	private final WindowEventHandler eventHandler;
	private final ScreenManager screenManager;

	private int width;
	private int height;
	private int framebufferWidth;
	private int framebufferHeight;
	private double guiScale = 1.0D;
	private int guiScaledWidth;
	private int guiScaledHeight;
	private int framerateLimit;
	private boolean vsync;
	private boolean fullscreen;
	private String errorSection = "";
	private Optional<VideoMode> preferredFullscreenVideoMode = Optional.empty();

	public Window(WindowEventHandler eventHandler, ScreenManager screenManager, DisplayData data,
			String videoModeName, String title) {
		this.eventHandler = eventHandler;
		this.screenManager = screenManager;
		refreshFramebufferSize();
		setGuiScale(1.0D);
	}

	/**
	 * The canvas can be resized by the document at any moment - a rotated phone, a dragged
	 * window edge - so its size is re-read rather than cached from construction.
	 */
	private void refreshFramebufferSize() {
		width = framebufferWidth = Math.max(1, PlatformInput.getWindowWidth());
		height = framebufferHeight = Math.max(1, PlatformInput.getWindowHeight());
	}

	/**
	 * Vanilla's version of this method is one call, RenderSystem.flipFrame(window), and it
	 * does three things that are not optional: it drains the input queue, it replays any
	 * render calls that were recorded off the render thread, and it clears the shared
	 * Tesselator buffer for the next frame. An earlier version of this class dropped all of
	 * it and called only PlatformInput.update(), which meant no key or mouse event ever
	 * reached the game and the Tesselator grew without bound.
	 *
	 * So flipFrame is called, exactly as vanilla does. The two GLFW calls inside it are this
	 * project's own (see org.lwjgl.glfw.GLFW): polling drains EaglercraftX's event queues
	 * into vanilla's callbacks, and swapping is a no-op because the browser presents the
	 * frame itself. The window handle is 0 and unused.
	 *
	 * PlatformInput.update() is the browser's half - vsync and the frame-rate limiter - and
	 * has no vanilla equivalent, so it stays alongside.
	 */
	public void updateDisplay() {
		com.mojang.blaze3d.systems.RenderSystem.flipFrame(0L);
		PlatformInput.update();

		/*
		 * Pump the integrated server. Without this, Singleplayer cannot start.
		 *
		 * The singleplayer server runs in a web worker (or, in single-thread mode, as a
		 * cooperative routine on this thread), and it talks to the client over a message
		 * queue rather than a socket. Nothing drains that queue by itself:
		 * SingleplayerServerController.runTick() is what collects the worker's IPC packets,
		 * feeds the local player's connection, and - in single-thread mode - is what gives
		 * the server any CPU time at all.
		 *
		 * 1.12.2 called it from Minecraft's own tick. That class is not reproduced in this
		 * port (2,768 decompiled lines whose locals lost their generics), so it goes here
		 * instead, which is the same point in the frame: vanilla's Minecraft.runTick() ends
		 * by calling window.updateDisplay(), so this runs exactly once per frame on the
		 * client thread, as it did before.
		 *
		 * Leaving it out was silent rather than fatal. The worker started, announced itself,
		 * and sent its IPC packets into a queue nobody read, so the client sat on "Starting
		 * integrated server..." for ever with no error anywhere - the server was running fine
		 * and simply could not be heard.
		 */
		net.lax1dude.eaglercraft.sp.SingleplayerServerController.runTick();

		int w = Math.max(1, PlatformInput.getWindowWidth());
		int h = Math.max(1, PlatformInput.getWindowHeight());
		if (w != framebufferWidth || h != framebufferHeight) {
			refreshFramebufferSize();
			setGuiScale(guiScale);
			eventHandler.resizeDisplay();
		}
	}

	public boolean shouldClose() {
		return PlatformInput.isCloseRequested();
	}

	public int getWidth() {
		return framebufferWidth;
	}

	public int getHeight() {
		return framebufferHeight;
	}

	public void setWidth(int width) {
		this.framebufferWidth = this.width = width;
	}

	public void setHeight(int height) {
		this.framebufferHeight = this.height = height;
	}

	/** The canvas fills what the page gives it, so screen size and window size agree. */
	public int getScreenWidth() {
		return framebufferWidth;
	}

	public int getScreenHeight() {
		return framebufferHeight;
	}

	public int getX() {
		return 0;
	}

	public int getY() {
		return 0;
	}

	public int getGuiScaledWidth() {
		return guiScaledWidth;
	}

	public int getGuiScaledHeight() {
		return guiScaledHeight;
	}

	public double getGuiScale() {
		return guiScale;
	}

	public void setGuiScale(double scale) {
		guiScale = scale;
		int w = (int) (framebufferWidth / scale);
		guiScaledWidth = framebufferWidth / scale > w ? w + 1 : w;
		int h = (int) (framebufferHeight / scale);
		guiScaledHeight = framebufferHeight / scale > h ? h + 1 : h;
	}

	/**
	 * Vanilla's scale search, unchanged: step up while the scaled surface still leaves at
	 * least 320x240 of GUI space, then round up to an even factor if a unicode font forced
	 * it. Keeping the algorithm identical means a world's saved GUI scale looks the same
	 * here as on the desktop client.
	 */
	public int calculateScale(int guiScaleIn, boolean forceUnicode) {
		int scale = 1;
		while (scale != guiScaleIn && scale < framebufferWidth && scale < framebufferHeight
				&& framebufferWidth / (scale + 1) >= 320
				&& framebufferHeight / (scale + 1) >= 240) {
			++scale;
		}
		if (forceUnicode && scale % 2 != 0) {
			++scale;
		}
		return scale;
	}

	public boolean isFullscreen() {
		return fullscreen;
	}

	public void toggleFullScreen() {
		fullscreen = !fullscreen;
		PlatformInput.toggleFullscreen();
	}

	/** Leaving fullscreen is all a page can do here; it cannot choose its own size. */
	public void setWindowed(int width, int height) {
		if (fullscreen) {
			toggleFullScreen();
		}
	}

	public void changeFullscreenVideoMode() {
	}

	public void updateVsync(boolean vsync) {
		this.vsync = vsync;
		PlatformInput.setVSync(vsync);
	}

	public void setFramerateLimit(int limit) {
		framerateLimit = limit;
	}

	public int getFramerateLimit() {
		return framerateLimit;
	}

	public int getRefreshRate() {
		return 60;
	}

	/**
	 * Raw mouse input means unaccelerated deltas from the OS. The browser exposes pointer
	 * lock, which EaglercraftX already turns on when the mouse is grabbed, and no separate
	 * raw-input switch, so there is nothing to toggle.
	 */
	public void updateRawMouseInput(boolean enabled) {
	}

	/** A page's title and icon belong to the document, not to the game. */
	public void setTitle(String title) {
	}

	public void setIcon(InputStream icon16, InputStream icon32) {
	}

	public Optional<VideoMode> getPreferredFullscreenVideoMode() {
		return preferredFullscreenVideoMode;
	}

	public void setPreferredFullscreenVideoMode(Optional<VideoMode> mode) {
		preferredFullscreenVideoMode = mode;
	}

	public Monitor findBestMonitor() {
		return null;
	}

	public long getWindow() {
		return 0L;
	}

	public void setErrorSection(String section) {
		errorSection = section;
	}

	/*
	 * The GLFW error callbacks. There is no GLFW, so nothing ever invokes these; they are
	 * kept because vanilla's crash handler and Minecraft's constructor both call them.
	 */

	public static void checkGlfwError(BiConsumer<Integer, String> consumer) {
	}

	public void defaultErrorCallback(int error, long description) {
		LOGGER.error("GLFW error {} during {}", error, errorSection);
	}

	public void setDefaultErrorCallback() {
	}

	@Override
	public void close() {
		PlatformRuntime.destroy();
	}
}
