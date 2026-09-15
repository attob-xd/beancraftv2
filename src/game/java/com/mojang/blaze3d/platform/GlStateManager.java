package com.mojang.blaze3d.platform;

import java.util.List;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;

import net.lax1dude.eaglercraft.internal.IBufferGL;
import net.lax1dude.eaglercraft.internal.IFramebufferGL;
import net.lax1dude.eaglercraft.internal.IProgramGL;
import net.lax1dude.eaglercraft.internal.IShaderGL;
import net.lax1dude.eaglercraft.internal.ITextureGL;
import net.lax1dude.eaglercraft.internal.IUniformGL;
import net.lax1dude.eaglercraft.internal.IVertexArrayGL;
import net.lax1dude.eaglercraft.internal.PlatformOpenGL;
import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.internal.GLObjectMap;

/**
 * Replaces blaze3d's GL state manager with one that talks to EaglercraftX's WebGL layer.
 *
 * Two mismatches make this more than a rename.
 *
 * The first is naming. Desktop GL identifies objects by integer names, and blaze3d's API
 * is built on that - _glGenBuffers() returns an int, _glBindBuffer takes one. WebGL has no
 * integer names at all; it hands back opaque objects (WebGLBuffer, WebGLProgram, ...),
 * which EaglercraftX wraps as IBufferGL, IProgramGL and so on. So this class keeps a
 * GLObjectMap per object kind - lax1dude's own int-to-object table, already used for
 * textures - and hands blaze3d the integer keys it expects. Textures go through
 * EaglercraftGPU's existing table rather than a new one, so its texture-binding cache
 * stays correct.
 *
 * The second is that 1.12.2 never used parts of GL that 1.18.2 does. Scissoring, the
 * stencil buffer and array uniforms had no bindings in EaglercraftX at all; they are added
 * to PlatformOpenGL alongside this (WebGL has had all three since WebGL 1).
 *
 * What genuinely has no WebGL counterpart is called out at each method: glPolygonMode,
 * glLogicOp, glDrawPixels, glGetTexImage and buffer mapping do not exist in GLES or WebGL,
 * and each is a no-op or throws rather than pretending to work.
 *
 * The member list is exactly what the vanilla jar calls, measured with
 * tools/scan_members.py - 105 of them.
 */
public class GlStateManager {

	private static final GLObjectMap<IBufferGL> BUFFERS = new GLObjectMap<>(256);
	private static final GLObjectMap<IVertexArrayGL> VERTEX_ARRAYS = new GLObjectMap<>(256);
	private static final GLObjectMap<IProgramGL> PROGRAMS = new GLObjectMap<>(64);
	private static final GLObjectMap<IShaderGL> SHADERS = new GLObjectMap<>(128);
	private static final GLObjectMap<IFramebufferGL> FRAMEBUFFERS = new GLObjectMap<>(64);
	private static final GLObjectMap<IUniformGL> UNIFORMS = new GLObjectMap<>(1024);


	/*
	 * Vanilla hands these methods java.nio buffers; EaglercraftX's GL layer takes its own.
	 * They are unrelated types - EaglercraftX does not implement java.nio, it replaces it -
	 * so the contents are copied across at the boundary. Uniforms are a few floats and the
	 * copy does not matter; a texture upload copies the image once more than a desktop build
	 * would, which is the price of the two buffer worlds not being the same.
	 */

	private static net.lax1dude.eaglercraft.internal.buffer.IntBuffer toEagler(
			java.nio.IntBuffer src) {
		if (src == null) {
			return null;
		}
		int len = src.remaining();
		net.lax1dude.eaglercraft.internal.buffer.IntBuffer dst = EagRuntime.allocateIntBuffer(len);
		int pos = src.position();
		for (int i = 0; i < len; ++i) {
			dst.put(src.get(pos + i));
		}
		dst.flip();
		return dst;
	}

	private static net.lax1dude.eaglercraft.internal.buffer.FloatBuffer toEagler(
			java.nio.FloatBuffer src) {
		if (src == null) {
			return null;
		}
		int len = src.remaining();
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer dst =
				EagRuntime.allocateFloatBuffer(len);
		int pos = src.position();
		for (int i = 0; i < len; ++i) {
			dst.put(src.get(pos + i));
		}
		dst.flip();
		return dst;
	}

	private static net.lax1dude.eaglercraft.internal.buffer.ByteBuffer toEagler(
			java.nio.ByteBuffer src) {
		if (src == null) {
			return null;
		}
		int len = src.remaining();
		net.lax1dude.eaglercraft.internal.buffer.ByteBuffer dst =
				EagRuntime.allocateByteBuffer(len);
		int pos = src.position();
		for (int i = 0; i < len; ++i) {
			dst.put(src.get(pos + i));
		}
		dst.flip();
		return dst;
	}


	/*
	 * The nested types. Vanilla reads these directly - blendState.srcRgb, Viewport.INSTANCE.
	 * They live here rather than in the jar because tools/trim_vanilla.sh removes every class
	 * this file replaces, and a nested class goes with its outer one. The GL enum values are
	 * the real ones; the state objects are plain holders, since the actual state lives in
	 * EaglercraftX's GlStateManager and is set through the methods below.
	 */

	public static class BooleanState {
		public boolean enabled;
		private final int cap;

		public BooleanState(int cap) {
			this.cap = cap;
		}

		public void disable() {
			setEnabled(false);
		}

		public void enable() {
			setEnabled(true);
		}

		public void setEnabled(boolean enabled) {
			if (enabled != this.enabled) {
				this.enabled = enabled;
				if (enabled) {
					PlatformOpenGL._wglEnable(cap);
				} else {
					PlatformOpenGL._wglDisable(cap);
				}
			}
		}
	}

	public static class BlendState {
		public final BooleanState mode = new BooleanState(3042 /* GL_BLEND */);
		public int srcRgb = 1;
		public int dstRgb = 0;
		public int srcAlpha = 1;
		public int dstAlpha = 0;
	}

	public static class ColorLogicState {
		public final BooleanState enable = new BooleanState(3058 /* GL_COLOR_LOGIC_OP */);
		public int op = 5379 /* GL_COPY */;
	}

	public static class ColorMask {
		public boolean red = true;
		public boolean green = true;
		public boolean blue = true;
		public boolean alpha = true;
	}

	public static class CullState {
		public final BooleanState enable = new BooleanState(2884 /* GL_CULL_FACE */);
		public int mode = 1029 /* GL_BACK */;
	}

	public static class DepthState {
		public final BooleanState mode = new BooleanState(2929 /* GL_DEPTH_TEST */);
		public boolean mask = true;
		public int func = 513 /* GL_LEQUAL */;
	}

	public static class PolygonOffsetState {
		public final BooleanState fill = new BooleanState(32823 /* GL_POLYGON_OFFSET_FILL */);
		public final BooleanState line = new BooleanState(10754 /* GL_POLYGON_OFFSET_LINE */);
		public float factor;
		public float units;
	}

	public static class ScissorState {
		public final BooleanState mode = new BooleanState(3089 /* GL_SCISSOR_TEST */);
	}

	public static class StencilFunc {
		public int func = 519 /* GL_ALWAYS */;
		public int ref;
		public int mask = -1;
	}

	public static class StencilState {
		public final StencilFunc func = new StencilFunc();
		public int mask = -1;
		public int fail = 7680 /* GL_KEEP */;
		public int zfail = 7680;
		public int zpass = 7680;
	}

	public static class TextureState {
		public boolean enable;
		public int binding;
	}

	public static class Viewport {
		public static final Viewport INSTANCE = new Viewport();
		public int x;
		public int y;
		public int width;
		public int height;

		private Viewport() {
		}
	}

	public enum SourceFactor {
		CONSTANT_ALPHA(32771), CONSTANT_COLOR(32769), DST_ALPHA(772), DST_COLOR(774), ONE(1),
		ONE_MINUS_CONSTANT_ALPHA(32772), ONE_MINUS_CONSTANT_COLOR(32770),
		ONE_MINUS_DST_ALPHA(773), ONE_MINUS_DST_COLOR(775), ONE_MINUS_SRC_ALPHA(771),
		ONE_MINUS_SRC_COLOR(769), SRC_ALPHA(770), SRC_ALPHA_SATURATE(776), SRC_COLOR(768),
		ZERO(0);

		public final int value;

		SourceFactor(int value) {
			this.value = value;
		}
	}

	public enum DestFactor {
		CONSTANT_ALPHA(32771), CONSTANT_COLOR(32769), DST_ALPHA(772), DST_COLOR(774), ONE(1),
		ONE_MINUS_CONSTANT_ALPHA(32772), ONE_MINUS_CONSTANT_COLOR(32770),
		ONE_MINUS_DST_ALPHA(773), ONE_MINUS_DST_COLOR(775), ONE_MINUS_SRC_ALPHA(771),
		ONE_MINUS_SRC_COLOR(769), SRC_ALPHA(770), SRC_ALPHA_SATURATE(776), SRC_COLOR(768),
		ZERO(0);

		public final int value;

		DestFactor(int value) {
			this.value = value;
		}
	}

	public enum LogicOp {
		AND(5377), AND_INVERTED(5380), AND_REVERSE(5378), CLEAR(5376), COPY(5379),
		COPY_INVERTED(5388), EQUIV(5385), INVERT(5386), NAND(5390), NOOP(5381), NOR(5384),
		OR(5383), OR_INVERTED(5389), OR_REVERSE(5387), SET(5391), XOR(5382);

		public final int value;

		LogicOp(int value) {
			this.value = value;
		}
	}

	// ---------------------------------------------------------------- capability state

	public static void _enableBlend() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.enableBlend();
	}

	public static void _disableBlend() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.disableBlend();
	}

	public static void _blendFunc(int src, int dst) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.blendFunc(src, dst);
	}

	public static void _blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb,
				srcAlpha, dstAlpha);
	}

	public static void _blendEquation(int mode) {
		PlatformOpenGL._wglBlendEquation(mode);
	}

	public static void _enableDepthTest() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.enableDepth();
	}

	public static void _disableDepthTest() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.disableDepth();
	}

	public static void _depthFunc(int func) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.depthFunc(func);
	}

	public static void _depthMask(boolean mask) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.depthMask(mask);
	}

	public static void _enableCull() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.enableCull();
	}

	public static void _disableCull() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.disableCull();
	}

	public static void _colorMask(boolean r, boolean g, boolean b, boolean a) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.colorMask(r, g, b, a);
	}

	public static void _enableScissorTest() {
		PlatformOpenGL._wglEnable(0x0C11 /* GL_SCISSOR_TEST */);
	}

	public static void _disableScissorTest() {
		PlatformOpenGL._wglDisable(0x0C11);
	}

	public static void _scissorBox(int x, int y, int width, int height) {
		PlatformOpenGL._wglScissor(x, y, width, height);
	}

	public static void _enablePolygonOffset() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.enablePolygonOffset();
	}

	public static void _disablePolygonOffset() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.disablePolygonOffset();
	}

	public static void _polygonOffset(float factor, float units) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.doPolygonOffset(factor, units);
	}

	/**
	 * GLES and WebGL have no glPolygonMode - wireframe rendering is not part of the API.
	 * Vanilla only reaches this from the F3+ debug renderers.
	 */
	public static void _polygonMode(int face, int mode) {
	}

	/**
	 * Likewise glLogicOp: WebGL has no colour logic operation. Vanilla uses it to invert
	 * the crosshair against the background, which simply renders normally instead.
	 */
	public static void _enableColorLogicOp() {
	}

	public static void _disableColorLogicOp() {
	}

	public static void _logicOp(int op) {
	}

	public static void _enableTexture() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.enableTexture2D();
	}

	public static void _disableTexture() {
		net.lax1dude.eaglercraft.opengl.GlStateManager.disableTexture2D();
	}

	// ---------------------------------------------------------------------- stencil

	public static void _stencilFunc(int func, int ref, int mask) {
		PlatformOpenGL._wglStencilFunc(func, ref, mask);
	}

	public static void _stencilMask(int mask) {
		PlatformOpenGL._wglStencilMask(mask);
	}

	public static void _stencilOp(int sfail, int dpfail, int dppass) {
		PlatformOpenGL._wglStencilOp(sfail, dpfail, dppass);
	}

	public static void _clearStencil(int value) {
		PlatformOpenGL._wglClearStencil(value);
	}

	// ------------------------------------------------------------------------ clear

	public static void _clear(int mask, boolean checkError) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.clear(mask);
	}

	public static void _clearColor(float r, float g, float b, float a) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.clearColor(r, g, b, a);
	}

	public static void _clearDepth(double depth) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.clearDepth((float) depth);
	}

	public static void _viewport(int x, int y, int width, int height) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.viewport(x, y, width, height);
	}

	// ---------------------------------------------------------------------- textures

	/**
	 * EaglercraftX's GlStateManager keeps the active unit in a package-private field, so
	 * the last value set is mirrored here rather than reaching into it.
	 */
	private static int activeTexture = 0x84C0 /* GL_TEXTURE0 */;

	public static void _activeTexture(int texture) {
		activeTexture = texture;
		net.lax1dude.eaglercraft.opengl.GlStateManager.setActiveTexture(texture);
	}

	public static int _getActiveTexture() {
		return activeTexture;
	}

	public static void _bindTexture(int texture) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.bindTexture(texture);
	}

	public static int _getTextureId(int unit) {
		return net.lax1dude.eaglercraft.opengl.GlStateManager.getBoundTexture();
	}

	public static int _genTexture() {
		return net.lax1dude.eaglercraft.opengl.GlStateManager.generateTexture();
	}

	public static void _genTextures(int[] out) {
		for (int i = 0; i < out.length; ++i) {
			out[i] = _genTexture();
		}
	}

	public static void _deleteTexture(int texture) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.deleteTexture(texture);
	}

	public static void _deleteTextures(int[] textures) {
		for (int texture : textures) {
			_deleteTexture(texture);
		}
	}

	public static void _texParameter(int target, int pname, int param) {
		PlatformOpenGL._wglTexParameteri(target, pname, param);
	}

	/**
	 * GL_TEXTURE_LOD_BIAS, the one parameter here that GLES 3 does not have.
	 *
	 * TextureUtil.prepareImage sets it to 0.0 on every mipmapped texture it allocates, so
	 * WebGL was answering with "INVALID_ENUM: texParameter: invalid parameter name" many
	 * times per resource reload. It is desktop-GL only: GLES 3.0 dropped per-texture LOD
	 * bias and offers no replacement outside a shader's textureLod.
	 *
	 * Dropping it costs nothing here. Vanilla only ever passes 0.0 - the value that means
	 * "no bias" - so honouring the call and ignoring it produce the same sampling; the bias
	 * vanilla actually varies is the mipmap level count, which goes through
	 * GL_TEXTURE_MAX_LEVEL / GL_TEXTURE_MAX_LOD above, and those GLES 3 does support.
	 */
	private static final int GL_TEXTURE_LOD_BIAS = 0x8501;

	public static void _texParameter(int target, int pname, float param) {
		if (pname == GL_TEXTURE_LOD_BIAS) {
			return;
		}
		PlatformOpenGL._wglTexParameterf(target, pname, param);
	}

	/*
	 * The proxy-texture probe.
	 *
	 * RenderSystem.maxSupportedTextureSize() finds the largest texture a driver will accept
	 * by uploading to GL_PROXY_TEXTURE_2D - which allocates nothing - at 32768, then 16384,
	 * and so on, asking after each whether the proxy came back with a non-zero width. WebGL
	 * has neither proxy textures nor glGetTexLevelParameter, so both calls threw and the
	 * client died building its main render target.
	 *
	 * The probe is answered rather than refused, because WebGL states the same fact directly:
	 * GL_MAX_TEXTURE_SIZE is the largest square texture the context supports. A proxy upload
	 * records the size asked for, and the query reports that size back when it fits and 0
	 * when it does not - which is what a driver's proxy texture does. vanilla's loop then
	 * settles on the answer it would reach on a desktop, without a special case in it.
	 */
	private static final int GL_PROXY_TEXTURE_2D = 0x8064;
	private static final int GL_TEXTURE_WIDTH = 0x1000;
	private static final int GL_TEXTURE_HEIGHT = 0x1001;
	private static final int GL_MAX_TEXTURE_SIZE = 0x0D33;

	private static int proxyTextureWidth;
	private static int proxyTextureHeight;

	private static final int GL_DEPTH_COMPONENT = 0x1902;
	private static final int GL_DEPTH_COMPONENT16 = 0x81A5;
	private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
	private static final int GL_DEPTH_COMPONENT32F = 0x8CAC;
	private static final int GL_UNSIGNED_SHORT = 0x1403;
	private static final int GL_UNSIGNED_INT = 0x1405;
	private static final int GL_FLOAT = 0x1406;

	/**
	 * Desktop GL accepts the unsized GL_DEPTH_COMPONENT as an internal format and picks a
	 * precision itself. GLES 3 - and so WebGL 2 - does not: a depth texture must be asked for
	 * by a sized format, and an unsized one produces a texture that cannot be attached. The
	 * symptom is not an error at upload but GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT later, when
	 * the framebuffer is checked, which is where the client died building its main render
	 * target.
	 *
	 * blaze3d's RenderTarget asks for (GL_DEPTH_COMPONENT, GL_FLOAT), so the size is chosen
	 * from the type it was going to store anyway: the result is the same depth buffer the
	 * desktop would have given it, named explicitly.
	 */
	private static int sizedDepthFormat(int internalFormat, int type) {
		if (internalFormat != GL_DEPTH_COMPONENT) {
			return internalFormat;
		}
		switch (type) {
			case GL_FLOAT:
				return GL_DEPTH_COMPONENT32F;
			case GL_UNSIGNED_INT:
				return GL_DEPTH_COMPONENT24;
			case GL_UNSIGNED_SHORT:
				return GL_DEPTH_COMPONENT16;
			default:
				return GL_DEPTH_COMPONENT24;
		}
	}

	public static void _texImage2D(int target, int level, int internalFormat, int width,
			int height, int border, int format, int type, java.nio.IntBuffer pixels) {
		if (target == GL_PROXY_TEXTURE_2D) {
			// A proxy upload never was a real one, and WebGL would reject the target
			// outright, so this must not reach the driver.
			proxyTextureWidth = width;
			proxyTextureHeight = height;
			return;
		}
		internalFormat = sizedDepthFormat(internalFormat, type);
		net.lax1dude.eaglercraft.internal.buffer.IntBuffer buf = toEagler(pixels);
		EaglercraftGPU.glTexImage2D(target, level, internalFormat, width, height, border,
				format, type, buf);
		if (buf != null) {
			EagRuntime.freeIntBuffer(buf);
		}
	}

	/**
	 * The offset form uploads from whatever buffer is bound to GL_PIXEL_UNPACK_BUFFER.
	 * WebGL 1 has no pixel-unpack buffer, and nothing in this build binds one, so an upload
	 * with no source data would silently produce a blank texture - better to say so.
	 */
	public static void _texSubImage2D(int target, int level, int x, int y, int width,
			int height, int format, int type, long pixels) {
		throw new UnsupportedOperationException(
				"texSubImage2D from a pixel-unpack buffer is not available in WebGL");
	}

	public static void _getTexImage(int target, int level, int format, int type, long pixels) {
		throw new UnsupportedOperationException(
				"glGetTexImage does not exist in GLES or WebGL; read back through a"
						+ " framebuffer instead");
	}

	/** Answers the proxy-texture probe; see _texImage2D above. */
	public static int _getTexLevelParameter(int target, int level, int pname) {
		if (target == GL_PROXY_TEXTURE_2D) {
			int max = PlatformOpenGL._wglGetInteger(GL_MAX_TEXTURE_SIZE);
			if (pname == GL_TEXTURE_WIDTH) {
				return proxyTextureWidth <= max ? proxyTextureWidth : 0;
			}
			if (pname == GL_TEXTURE_HEIGHT) {
				return proxyTextureHeight <= max ? proxyTextureHeight : 0;
			}
			return 0;
		}
		throw new UnsupportedOperationException("glGetTexLevelParameter does not exist in GLES"
				+ " or WebGL, and only the proxy-texture probe is emulated (target 0x"
				+ Integer.toHexString(target) + ")");
	}

	public static void _pixelStore(int pname, int param) {
		PlatformOpenGL._wglPixelStorei(pname, param);
	}

	public static void _readPixels(int x, int y, int width, int height, int format, int type,
			java.nio.ByteBuffer pixels) {
		net.lax1dude.eaglercraft.internal.buffer.ByteBuffer buf =
				EagRuntime.allocateByteBuffer(pixels.remaining());
		PlatformOpenGL._wglReadPixels(x, y, width, height, format, type, buf);
		int pos = pixels.position();
		for (int i = 0; i < buf.limit(); ++i) {
			pixels.put(pos + i, buf.get(i));
		}
		EagRuntime.freeByteBuffer(buf);
	}

	public static void _readPixels(int x, int y, int width, int height, int format, int type,
			long pixels) {
		throw new UnsupportedOperationException(
				"glReadPixels into a pixel-pack buffer is not available in WebGL");
	}

	/** glDrawPixels was removed in core GL and never existed in GLES. */
	public static void _glDrawPixels(int width, int height, int format, int type, long pixels) {
	}

	// ----------------------------------------------------------------------- buffers

	public static int _glGenBuffers() {
		return BUFFERS.register(PlatformOpenGL._wglGenBuffers());
	}

	public static void _glBindBuffer(int target, int buffer) {
		PlatformOpenGL._wglBindBuffer(target, buffer == 0 ? null : BUFFERS.get(buffer));
	}

	public static void _glBufferData(int target, java.nio.ByteBuffer data, int usage) {
		net.lax1dude.eaglercraft.internal.buffer.ByteBuffer buf = toEagler(data);
		PlatformOpenGL._wglBufferData(target, buf, usage);
		if (buf != null) {
			EagRuntime.freeByteBuffer(buf);
		}
	}

	public static void _glBufferData(int target, long size, int usage) {
		mappedSizes.put(target, (int) size);
		PlatformOpenGL._wglBufferData(target, (int) size, usage);
	}

	public static void _glDeleteBuffers(int buffer) {
		IBufferGL obj = BUFFERS.free(buffer);
		if (obj != null) {
			PlatformOpenGL._wglDeleteBuffers(obj);
		}
	}

	/*
	 * Buffer mapping, emulated.
	 *
	 * WebGL cannot map a buffer into client memory - there is no shared address space to map
	 * it into - and this used to return null on the assumption that blaze3d would fall back
	 * to glBufferData. It does not: RenderSystem's AutoStorageIndexBuffer maps the element
	 * buffer to write its indices and throws "Failed to map GL buffer" on null, which is
	 * where the client died while building the shared index buffer, before drawing anything.
	 *
	 * So the mapping is emulated the way it always is on an API without it: hand back an
	 * ordinary client buffer of the size the last glBufferData asked for, let the caller fill
	 * it, and upload the whole thing with glBufferSubData when it unmaps. The caller cannot
	 * tell the difference; the cost is one extra copy per map, on a path that runs when a
	 * buffer grows rather than per frame.
	 *
	 * The size comes from glBufferData because that is the only place WebGL is told it - a
	 * mapped range has no length of its own here. Mapping a buffer that was never sized is a
	 * caller error, and returning null for it keeps vanilla's own "Failed to map" message.
	 */
	private static final java.util.Map<Integer, Integer> mappedSizes = new java.util.HashMap<>();
	private static final java.util.Map<Integer, java.nio.ByteBuffer> mappedBuffers =
			new java.util.HashMap<>();

	public static java.nio.ByteBuffer _glMapBuffer(int target, int access) {
		Integer size = mappedSizes.get(target);
		if (size == null || size <= 0) {
			return null;
		}
		java.nio.ByteBuffer mapped = java.nio.ByteBuffer.allocateDirect(size)
				.order(java.nio.ByteOrder.nativeOrder());
		mappedBuffers.put(target, mapped);
		return mapped;
	}

	public static void _glUnmapBuffer(int target) {
		java.nio.ByteBuffer mapped = mappedBuffers.remove(target);
		if (mapped == null) {
			return;
		}
		mapped.position(0);
		mapped.limit(mapped.capacity());
		net.lax1dude.eaglercraft.internal.buffer.ByteBuffer buf = toEagler(mapped);
		PlatformOpenGL._wglBufferSubData(target, 0, buf);
		if (buf != null) {
			EagRuntime.freeByteBuffer(buf);
		}
	}

	// ------------------------------------------------------------------ vertex arrays

	public static int _glGenVertexArrays() {
		return VERTEX_ARRAYS.register(PlatformOpenGL._wglGenVertexArrays());
	}

	public static void _glBindVertexArray(int array) {
		PlatformOpenGL._wglBindVertexArray(array == 0 ? null : VERTEX_ARRAYS.get(array));
	}

	public static void _glDeleteVertexArrays(int array) {
		IVertexArrayGL obj = VERTEX_ARRAYS.free(array);
		if (obj != null) {
			PlatformOpenGL._wglDeleteVertexArrays(obj);
		}
	}

	public static void _enableVertexAttribArray(int index) {
		PlatformOpenGL._wglEnableVertexAttribArray(index);
	}

	public static void _disableVertexAttribArray(int index) {
		PlatformOpenGL._wglDisableVertexAttribArray(index);
	}

	public static void _vertexAttribPointer(int index, int size, int type, boolean normalized,
			int stride, long offset) {
		PlatformOpenGL._wglVertexAttribPointer(index, size, type, normalized, stride,
				(int) offset);
	}

	/**
	 * Integer vertex attributes, and this must be the real glVertexAttribIPointer.
	 *
	 * An earlier version of this method fed the attribute through the float
	 * vertexAttribPointer, reasoning that EaglercraftX's GL layer had no integer binding and
	 * 1.12.2's formats were always float. That compiled, linked, and drew nothing: every
	 * vanilla shader that reads the lightmap or overlay declares "in ivec2 UV2" / "in ivec2
	 * UV1", and WebGL 2 checks at draw time that an integer shader attribute is backed by an
	 * integer pointer. When it is not, drawElements fails with INVALID_OPERATION and the batch
	 * is silently dropped - no exception, no log line, just no text on any button and no
	 * entity on any screen. The GUI's blit quads survived only because position_tex has no
	 * integer inputs, which is what made the menu look "almost right".
	 *
	 * This was found by wrapping every WebGL call in the live page and counting errors per
	 * function: exactly one drawElements per frame failed, with an index count equal to the
	 * menu's text batch. The check is easy to reproduce that way and impossible to see from
	 * the Java side.
	 *
	 * Vanilla's VertexFormatElement.Usage.UV chooses this call for any non-float UV type, so
	 * the same fix covers UV1 and UV2 in every entity, text, particle and terrain format.
	 * Types reaching here are GL_SHORT (UV1/UV2); WebGL 2 accepts BYTE, SHORT, INT and their
	 * unsigned forms.
	 */
	public static void _vertexAttribIPointer(int index, int size, int type, int stride,
			long offset) {
		PlatformOpenGL._wglVertexAttribIPointer(index, size, type, stride, (int) offset);
	}

	public static void _drawElements(int mode, int count, int type, long indices) {
		PlatformOpenGL._wglDrawElements(mode, count, type, (int) indices);
	}

	// ------------------------------------------------------------------ framebuffers

	public static int glGenFramebuffers() {
		return FRAMEBUFFERS.register(PlatformOpenGL._wglCreateFramebuffer());
	}

	public static void _glBindFramebuffer(int target, int framebuffer) {
		PlatformOpenGL._wglBindFramebuffer(target,
				framebuffer == 0 ? null : FRAMEBUFFERS.get(framebuffer));
	}

	public static void _glDeleteFramebuffers(int framebuffer) {
		IFramebufferGL obj = FRAMEBUFFERS.free(framebuffer);
		if (obj != null) {
			PlatformOpenGL._wglDeleteFramebuffer(obj);
		}
	}

	public static void _glFramebufferTexture2D(int target, int attachment, int texTarget,
			int texture, int level) {
		ITextureGL tex = texture == 0 ? null : EaglercraftGPU.getNativeTexture(texture);
		PlatformOpenGL._wglFramebufferTexture2D(target, attachment, texTarget, tex, level);
	}

	public static int glCheckFramebufferStatus(int target) {
		return PlatformOpenGL._wglCheckFramebufferStatus(target);
	}

	public static void _glBlitFrameBuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0,
			int dstY0, int dstX1, int dstY1, int mask, int filter) {
		PlatformOpenGL._wglBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1,
				dstY1, mask, filter);
	}

	// ---------------------------------------------------------------------- shaders

	public static int glCreateShader(int type) {
		return SHADERS.register(PlatformOpenGL._wglCreateShader(type));
	}

	/**
	 * Vanilla's shaders are desktop GLSL and WebGL only speaks GLSL ES, so the source is
	 * translated on the way through. This is the one place it can be done: every core and post
	 * shader reaches the driver here, via Program.compileShaderInternal and EffectProgram,
	 * while EaglercraftX's own shaders go straight to PlatformOpenGL and are already ES.
	 *
	 * All 151 shader files under assets/minecraft/shaders declare "#version 150". WebGL 2
	 * exposes GLSL ES 3.00 and refuses that - "'150' : client/version number not supported" -
	 * so every shader failed and the client died at "could not preload blit shader". Two
	 * changes make them compile, and they needed nothing else:
	 *
	 *   - the version directive becomes "#version 300 es";
	 *   - default precision declarations are added, which GLSL ES fragment shaders require
	 *     and desktop GLSL does not ("No precision specified for (float)").
	 *
	 * Everything else is already ES 3.00 syntax - in/out rather than varying/attribute,
	 * texture() rather than texture2D(), an explicit out for the fragment colour rather than
	 * gl_FragColor. The only unusual constructs across all of them are "flat" in two files and
	 * gl_VertexID in one, both of which ES 3.00 has.
	 *
	 * Measured, not assumed: every shader was resolved through its #moj_import chain by
	 * tools/dump_resolved_shaders.py and handed to a real WebGL 2 context. 0 of 147 compiled
	 * untranslated; 147 of 147 compiled after this; and all 62 vertex/fragment pairs linked.
	 *
	 * highp rather than mediump because ES 3.00 guarantees highp in fragment shaders, and
	 * Minecraft's fog and lighting maths is written for desktop float precision. sampler2D is
	 * declared too - ES defaults samplers to lowp, which would quietly cost texture precision.
	 */
	public static void glShaderSource(int shader, List<String> source) {
		StringBuilder sb = new StringBuilder();
		for (String part : source) {
			sb.append(part);
		}
		PlatformOpenGL._wglShaderSource(SHADERS.get(shader), toGlslEs(sb.toString()));
	}

	private static final String GLSL_ES_HEADER = "#version 300 es\n"
			+ "precision highp float;\n"
			+ "precision highp sampler2D;\n"
			+ "precision highp int;";

	/**
	 * Swaps the first #version directive for an ES one.
	 *
	 * The directive is found by scanning line starts rather than with indexOf("#version 150"),
	 * which is what this used to do. Two reasons, both about source this does not control: a
	 * resource pack may ship core shaders of its own at some other version, and vanilla's own
	 * GlslPreprocessor rewrites the number to the highest any imported chunk declares, so
	 * "150" is only the answer as long as every file agrees on it. A literal match also finds
	 * "#version 150" inside a comment - which vanilla itself produces, since processVersions
	 * comments out the directive of every imported chunk.
	 *
	 * Source with no directive at all is left alone. Vanilla does not produce that, and
	 * prepending a version to source that already began with code would be worse than letting
	 * the driver say so.
	 *
	 * Known cost, not worth paying for yet: the header is four lines where the directive was
	 * one, so a driver's error line numbers run three high until vanilla's own #line directive
	 * for the first import resets them. Emitting "#line 2" after the header would fix that.
	 * It is left out because every shipped shader compiles, so the numbers are only read when
	 * a resource pack brings its own - add it the first time that actually happens.
	 */
	private static String toGlslEs(String source) {
		int directive = indexOfVersionDirective(source);
		if (directive < 0) {
			return source;
		}
		int endOfLine = source.indexOf('\n', directive);
		String rest = endOfLine < 0 ? "" : source.substring(endOfLine);
		return source.substring(0, directive) + GLSL_ES_HEADER + rest;
	}

	/**
	 * Index of the '#' of the first #version directive that starts a line, or -1. Only spaces
	 * and tabs may precede it, and may sit between the '#' and the keyword, which is what the
	 * GLSL grammar allows and what vanilla's own version regex accepts.
	 */
	private static int indexOfVersionDirective(String source) {
		int i = 0;
		int n = source.length();
		while (i < n) {
			int lineEnd = source.indexOf('\n', i);
			if (lineEnd < 0) {
				lineEnd = n;
			}
			int j = i;
			while (j < lineEnd && (source.charAt(j) == ' ' || source.charAt(j) == '\t')) {
				++j;
			}
			if (j < lineEnd && source.charAt(j) == '#') {
				int k = j + 1;
				while (k < lineEnd && (source.charAt(k) == ' ' || source.charAt(k) == '\t')) {
					++k;
				}
				if (source.startsWith("version", k)) {
					return j;
				}
			}
			i = lineEnd + 1;
		}
		return -1;
	}

	public static void glCompileShader(int shader) {
		PlatformOpenGL._wglCompileShader(SHADERS.get(shader));
	}

	public static int glGetShaderi(int shader, int pname) {
		return PlatformOpenGL._wglGetShaderi(SHADERS.get(shader), pname);
	}

	public static String glGetShaderInfoLog(int shader, int maxLength) {
		return PlatformOpenGL._wglGetShaderInfoLog(SHADERS.get(shader));
	}

	public static void glDeleteShader(int shader) {
		IShaderGL obj = SHADERS.free(shader);
		if (obj != null) {
			PlatformOpenGL._wglDeleteShader(obj);
		}
	}

	public static int glCreateProgram() {
		return PROGRAMS.register(PlatformOpenGL._wglCreateProgram());
	}

	public static void glAttachShader(int program, int shader) {
		PlatformOpenGL._wglAttachShader(PROGRAMS.get(program), SHADERS.get(shader));
	}

	public static void glLinkProgram(int program) {
		PlatformOpenGL._wglLinkProgram(PROGRAMS.get(program));
	}

	public static int glGetProgrami(int program, int pname) {
		return PlatformOpenGL._wglGetProgrami(PROGRAMS.get(program), pname);
	}

	public static String glGetProgramInfoLog(int program, int maxLength) {
		return PlatformOpenGL._wglGetProgramInfoLog(PROGRAMS.get(program));
	}

	public static void glDeleteProgram(int program) {
		IProgramGL obj = PROGRAMS.free(program);
		if (obj != null) {
			PlatformOpenGL._wglDeleteProgram(obj);
		}
	}

	public static void _glUseProgram(int program) {
		PlatformOpenGL._wglUseProgram(program == 0 ? null : PROGRAMS.get(program));
	}

	public static void _glBindAttribLocation(int program, int index, CharSequence name) {
		PlatformOpenGL._wglBindAttribLocation(PROGRAMS.get(program), index, name.toString());
	}

	public static int _glGetAttribLocation(int program, CharSequence name) {
		return PlatformOpenGL._wglGetAttribLocation(PROGRAMS.get(program), name.toString());
	}

	/**
	 * Uniform locations are integers in GL and opaque objects in WebGL, so they go through
	 * the same table as everything else. A location that does not exist is -1 in GL, and
	 * blaze3d checks for that, so a null lookup maps to -1 rather than to a table entry.
	 */
	public static int _glGetUniformLocation(int program, CharSequence name) {
		IUniformGL loc = PlatformOpenGL._wglGetUniformLocation(PROGRAMS.get(program),
				name.toString());
		return loc == null ? -1 : UNIFORMS.register(loc);
	}

	private static IUniformGL uniform(int location) {
		return location < 0 ? null : UNIFORMS.get(location);
	}

	public static void _glUniform1i(int location, int value) {
		PlatformOpenGL._wglUniform1i(uniform(location), value);
	}

	public static void _glUniform1(int location, java.nio.IntBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.IntBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform1iv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeIntBuffer(buf);
		}
	}

	public static void _glUniform2(int location, java.nio.IntBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.IntBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform2iv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeIntBuffer(buf);
		}
	}

	public static void _glUniform3(int location, java.nio.IntBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.IntBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform3iv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeIntBuffer(buf);
		}
	}

	public static void _glUniform4(int location, java.nio.IntBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.IntBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform4iv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeIntBuffer(buf);
		}
	}

	public static void _glUniform1(int location, java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform1fv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	public static void _glUniform2(int location, java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform2fv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	public static void _glUniform3(int location, java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform3fv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	public static void _glUniform4(int location, java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniform4fv(uniform(location), buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	public static void _glUniformMatrix2(int location, boolean transpose,
			java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniformMatrix2fv(uniform(location), transpose, buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	public static void _glUniformMatrix3(int location, boolean transpose,
			java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniformMatrix3fv(uniform(location), transpose, buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	public static void _glUniformMatrix4(int location, boolean transpose,
			java.nio.FloatBuffer values) {
		net.lax1dude.eaglercraft.internal.buffer.FloatBuffer buf = toEagler(values);
		PlatformOpenGL._wglUniformMatrix4fv(uniform(location), transpose, buf);
		if (buf != null) {
			EagRuntime.freeFloatBuffer(buf);
		}
	}

	// ------------------------------------------------------------------------ queries

	public static int _getError() {
		return PlatformOpenGL._wglGetError();
	}

	public static String _getString(int name) {
		return PlatformOpenGL._wglGetString(name);
	}

	public static int _getInteger(int name) {
		return PlatformOpenGL._wglGetInteger(name);
	}

	// ------------------------------------------------------------------------ lighting

	/*
	 * 1.18.2 sets up its diffuse lighting by handing two or three direction vectors to the
	 * core shaders, which read them as uniforms. EaglercraftX's pipeline carries its own
	 * light directions through FixedFunctionPipeline, set by GlStateManager.setMCLight, so
	 * these forward there rather than touching the vanilla shader uniforms.
	 */

	public static void setupGuiFlatDiffuseLighting(Vector3f light0, Vector3f light1) {
		net.lax1dude.eaglercraft.opengl.GlStateManager.enableMCLight(0, 0.4F, light0.x(),
				light0.y(), light0.z(), 0.0F);
		net.lax1dude.eaglercraft.opengl.GlStateManager.enableMCLight(1, 0.4F, light1.x(),
				light1.y(), light1.z(), 0.0F);
		net.lax1dude.eaglercraft.opengl.GlStateManager.setMCLightAmbient(0.6F, 0.6F, 0.6F);
	}

	public static void setupGui3DDiffuseLighting(Vector3f light0, Vector3f light1) {
		setupGuiFlatDiffuseLighting(light0, light1);
	}

	public static void setupLevelDiffuseLighting(Vector3f light0, Vector3f light1,
			Matrix4f modelView) {
		setupGuiFlatDiffuseLighting(light0, light1);
	}
}
