package org.lwjgl.opengl;

import java.nio.IntBuffer;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * Replaces the sliver of LWJGL's OpenGL 1.1 binding that vanilla still calls directly.
 *
 * Almost all of blaze3d goes through GlStateManager, which this port already maps onto
 * EaglercraftX's WebGL layer. Four calls do not - blaze3d's own TextureUtil reaches past
 * GlStateManager to upload the window icon and the Mojang logo, and GlDebug calls glEnable -
 * so those four are routed back through GlStateManager here rather than duplicating the
 * WebGL handle bookkeeping it already does.
 */
public class GL11 {

	protected GL11() {
	}

	public static void glTexParameteri(int target, int pname, int param) {
		GlStateManager._texParameter(target, pname, param);
	}

	public static void glPixelStorei(int pname, int param) {
		GlStateManager._pixelStore(pname, param);
	}

	public static void glTexImage2D(int target, int level, int internalFormat, int width,
			int height, int border, int format, int type, IntBuffer pixels) {
		GlStateManager._texImage2D(target, level, internalFormat, width, height, border, format,
				type, pixels);
	}

	/**
	 * The only caller is GlDebug, enabling GL_DEBUG_OUTPUT_SYNCHRONOUS - a capability WebGL
	 * does not have, and one this build never reaches because GLX._init does not install a
	 * debug callback. Enabling an unknown capability in WebGL raises INVALID_ENUM, so this
	 * does nothing rather than poison the error state for the next real call.
	 */
	public static void glEnable(int cap) {
	}
}
