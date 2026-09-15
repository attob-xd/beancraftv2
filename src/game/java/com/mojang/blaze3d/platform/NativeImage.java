package com.mojang.blaze3d.platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Base64;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.internal.buffer.IntBuffer;
import net.lax1dude.eaglercraft.opengl.EaglercraftGPU;
import net.lax1dude.eaglercraft.opengl.ImageData;

/**
 * Replaces blaze3d's NativeImage, which is a block of off-heap memory decoded and encoded
 * by STB and uploaded with LWJGL.
 *
 * There is no off-heap memory to hand STB here, and no STB. What there is instead is the
 * browser's own PNG decoder, which EaglercraftX already drives through ImageData - it
 * hands a decoded image back as an int[]. So this holds an int[] and does its own pixel
 * work.
 *
 * Pixel order matters and is easy to get wrong. Vanilla stores RGBA bytes in memory and
 * reads them back as ints, which on a little-endian machine makes getPixelRGBA() return
 * 0xAABBGGRR - that is why NativeImage.combine takes its arguments as (a, b, g, r). This
 * class keeps that exact layout so every caller's masking and shifting stays correct, and
 * converts to ImageData's ARGB only at the two boundaries where it has to.
 *
 * What is not supported, and says so: copyFromFont (STB TrueType - see
 * TrueTypeGlyphProvider), and downloading a texture back out of GL, which WebGL only
 * allows through a framebuffer.
 */
public final class NativeImage implements AutoCloseable {

	private static final Logger LOGGER = LogUtils.getLogger();

	public enum Format {
		RGBA(4, 6408 /* GL_RGBA */, true, true, true, false, true, 0, 8, 16, 255, 24, true),
		RGB(3, 6407 /* GL_RGB */, true, true, true, false, false, 0, 8, 16, 0, 255, true),
		LUMINANCE_ALPHA(2, 6410 /* GL_LUMINANCE_ALPHA */, false, false, false, true, true, 255,
				255, 255, 0, 8, true),
		LUMINANCE(1, 6409 /* GL_LUMINANCE */, false, false, false, true, false, 0, 0, 0, 0, 255,
				true);

		final int components;
		private final int glFormat;
		private final boolean hasRed;
		private final boolean hasGreen;
		private final boolean hasBlue;
		private final boolean hasLuminance;
		private final boolean hasAlpha;
		private final int redOffset;
		private final int greenOffset;
		private final int blueOffset;
		private final int luminanceOffset;
		private final int alphaOffset;
		private final boolean supportedByStb;

		Format(int components, int glFormat, boolean hasRed, boolean hasGreen, boolean hasBlue,
				boolean hasLuminance, boolean hasAlpha, int redOffset, int greenOffset,
				int blueOffset, int luminanceOffset, int alphaOffset, boolean supportedByStb) {
			this.components = components;
			this.glFormat = glFormat;
			this.hasRed = hasRed;
			this.hasGreen = hasGreen;
			this.hasBlue = hasBlue;
			this.hasLuminance = hasLuminance;
			this.hasAlpha = hasAlpha;
			this.redOffset = redOffset;
			this.greenOffset = greenOffset;
			this.blueOffset = blueOffset;
			this.luminanceOffset = luminanceOffset;
			this.alphaOffset = alphaOffset;
			this.supportedByStb = supportedByStb;
		}

		public int components() {
			return components;
		}

		public int glFormat() {
			return glFormat;
		}

		public boolean hasAlpha() {
			return hasAlpha;
		}

		public boolean hasLuminance() {
			return hasLuminance;
		}

		public boolean hasLuminanceOrAlpha() {
			return hasLuminance || hasAlpha;
		}

		public boolean hasLuminanceOrRed() {
			return hasLuminance || hasRed;
		}

		public boolean hasLuminanceOrGreen() {
			return hasLuminance || hasGreen;
		}

		public boolean hasLuminanceOrBlue() {
			return hasLuminance || hasBlue;
		}

		public int alphaOffset() {
			return alphaOffset;
		}

		public int luminanceOffset() {
			return luminanceOffset;
		}

		public int luminanceOrAlphaOffset() {
			return hasLuminance ? luminanceOffset : alphaOffset;
		}

		public int luminanceOrRedOffset() {
			return hasLuminance ? luminanceOffset : redOffset;
		}

		public int luminanceOrGreenOffset() {
			return hasLuminance ? luminanceOffset : greenOffset;
		}

		public int luminanceOrBlueOffset() {
			return hasLuminance ? luminanceOffset : blueOffset;
		}

		public boolean supportedByStb() {
			return supportedByStb;
		}

		/**
		 * Pixels here are always tightly packed 32-bit RGBA, because that is what the
		 * browser's decoder produces, so there is no row alignment to set.
		 */
		public void setPackPixelStoreState() {
		}

		public void setUnpackPixelStoreState() {
		}

		public static Format getStbFormat(int components) {
			switch (components) {
			case 1:
				return LUMINANCE;
			case 2:
				return LUMINANCE_ALPHA;
			case 3:
				return RGB;
			default:
				return RGBA;
			}
		}
	}

	public enum InternalGlFormat {
		RGBA(6408), RGB(6407), RG(33319), RED(6403);

		private final int glFormat;

		InternalGlFormat(int glFormat) {
			this.glFormat = glFormat;
		}

		public int glFormat() {
			return glFormat;
		}
	}

	/**
	 * Vanilla's STB write callback, which collected encoded bytes from native memory.
	 * Nothing writes PNGs through STB here; see writeToFile.
	 */
	public static final class WriteCallback {
		public long address() {
			return 0L;
		}

		public void free() {
		}

		public void throwIfException() {
		}
	}

	private final Format format;
	private final int width;
	private final int height;
	private int[] pixels;

	public NativeImage(int width, int height, boolean unused) {
		this(Format.RGBA, width, height, unused);
	}

	public NativeImage(Format format, int width, int height, boolean unused) {
		this.format = format;
		this.width = width;
		this.height = height;
		this.pixels = new int[width * height];
	}

	private NativeImage(Format format, int width, int height, int[] pixels) {
		this.format = format;
		this.width = width;
		this.height = height;
		this.pixels = pixels;
	}

	public static NativeImage read(InputStream input) throws IOException {
		return read(Format.RGBA, input);
	}

	public static NativeImage read(Format format, InputStream input) throws IOException {
		ImageData img = ImageData.loadImageFile(input);
		if (img == null) {
			throw new IOException("Could not decode the image");
		}
		return new NativeImage(format, img.width, img.height, argbToAbgr(img.pixels));
	}

	public static NativeImage read(byte[] data) throws IOException {
		ImageData img = ImageData.loadImageFile(data);
		if (img == null) {
			throw new IOException("Could not decode the image");
		}
		return new NativeImage(Format.RGBA, img.width, img.height, argbToAbgr(img.pixels));
	}

	public static NativeImage fromBase64(String base64) throws IOException {
		return read(Base64.getDecoder().decode(base64.replaceAll("\\s", "")));
	}

	/**
	 * ImageData decodes to ARGB, which is what a browser canvas hands back. Vanilla's
	 * layout is ABGR (see the class comment), so red and blue swap.
	 */
	private static int[] argbToAbgr(int[] argb) {
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; ++i) {
			int p = argb[i];
			out[i] = (p & 0xFF00FF00) | ((p >> 16) & 0xFF) | ((p & 0xFF) << 16);
		}
		return out;
	}

	private static int[] abgrToArgb(int[] abgr) {
		return argbToAbgr(abgr); // the swap is its own inverse
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public Format format() {
		return format;
	}

	private void checkOpen() {
		if (pixels == null) {
			throw new IllegalStateException("This NativeImage has already been closed");
		}
	}

	public int getPixelRGBA(int x, int y) {
		checkOpen();
		return pixels[y * width + x];
	}

	public void setPixelRGBA(int x, int y, int abgr) {
		checkOpen();
		pixels[y * width + x] = abgr;
	}

	public byte getLuminanceOrAlpha(int x, int y) {
		return (byte) (getPixelRGBA(x, y) >> format.luminanceOrAlphaOffset());
	}

	public int[] makePixelArray() {
		checkOpen();
		return abgrToArgb(pixels);
	}

	public byte[] asByteArray() {
		checkOpen();
		byte[] out = new byte[pixels.length * 4];
		for (int i = 0; i < pixels.length; ++i) {
			int p = pixels[i];
			out[i * 4] = (byte) p;
			out[i * 4 + 1] = (byte) (p >> 8);
			out[i * 4 + 2] = (byte) (p >> 16);
			out[i * 4 + 3] = (byte) (p >> 24);
		}
		return out;
	}

	public static int combine(int a, int b, int g, int r) {
		return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	public static int getR(int abgr) {
		return abgr & 0xFF;
	}

	public static int getG(int abgr) {
		return (abgr >> 8) & 0xFF;
	}

	public static int getB(int abgr) {
		return (abgr >> 16) & 0xFF;
	}

	public static int getA(int abgr) {
		return (abgr >>> 24) & 0xFF;
	}

	public void fillRect(int x, int y, int w, int h, int abgr) {
		checkOpen();
		for (int yy = y; yy < y + h; ++yy) {
			for (int xx = x; xx < x + w; ++xx) {
				pixels[yy * width + xx] = abgr;
			}
		}
	}

	public void copyFrom(NativeImage other) {
		checkOpen();
		other.checkOpen();
		System.arraycopy(other.pixels, 0, pixels, 0,
				Math.min(pixels.length, other.pixels.length));
	}

	public void copyRect(int dx, int dy, int offsetX, int offsetY, int w, int h, boolean flipX,
			boolean flipY) {
		checkOpen();
		for (int yy = 0; yy < h; ++yy) {
			for (int xx = 0; xx < w; ++xx) {
				int srcX = flipX ? w - 1 - xx : xx;
				int srcY = flipY ? h - 1 - yy : yy;
				int p = getPixelRGBA(dx + xx, dy + yy);
				setPixelRGBA(dx + offsetX + srcX, dy + offsetY + srcY, p);
			}
		}
	}

	public void flipY() {
		checkOpen();
		int[] row = new int[width];
		for (int y = 0; y < height / 2; ++y) {
			System.arraycopy(pixels, y * width, row, 0, width);
			System.arraycopy(pixels, (height - 1 - y) * width, pixels, y * width, width);
			System.arraycopy(row, 0, pixels, (height - 1 - y) * width, width);
		}
	}

	/** Nearest-neighbour, which is what vanilla's mipmap and icon paths expect. */
	public void resizeSubRectTo(int x, int y, int w, int h, NativeImage target) {
		checkOpen();
		for (int ty = 0; ty < target.height; ++ty) {
			for (int tx = 0; tx < target.width; ++tx) {
				int sx = x + tx * w / target.width;
				int sy = y + ty * h / target.height;
				target.setPixelRGBA(tx, ty, getPixelRGBA(sx, sy));
			}
		}
	}

	public void upload(int level, int x, int y, boolean autoClose) {
		upload(level, x, y, 0, 0, width, height, false, autoClose);
	}

	/**
	 * Vanilla's, with its GL constants spelled out.
	 *
	 * <p>{@code blur} picks GL_LINEAR over GL_NEAREST, and {@code mipmap} picks the
	 * mipmapping variant of the minification filter. Minecraft wants GL_NEAREST nearly
	 * everywhere - it is pixel art, and smoothing it is exactly the "why is everything blurry"
	 * complaint - so the default of blur=false matters.
	 */
	private static void setFilter(boolean blur, boolean mipmap) {
		if (blur) {
			GlStateManager._texParameter(3553, 10241 /* MIN_FILTER */,
					mipmap ? 9987 /* LINEAR_MIPMAP_LINEAR */ : 9729 /* LINEAR */);
			GlStateManager._texParameter(3553, 10240 /* MAG_FILTER */, 9729 /* LINEAR */);
		} else {
			GlStateManager._texParameter(3553, 10241 /* MIN_FILTER */,
					mipmap ? 9986 /* NEAREST_MIPMAP_LINEAR */ : 9728 /* NEAREST */);
			GlStateManager._texParameter(3553, 10240 /* MAG_FILTER */, 9728 /* NEAREST */);
		}
	}

	public void upload(int level, int x, int y, int skipX, int skipY, int w, int h,
			boolean mipmap, boolean autoClose) {
		upload(level, x, y, skipX, skipY, w, h, false, mipmap, false, autoClose);
	}

	/**
	 * Writes this image, or a rectangle of it, into the currently bound texture.
	 *
	 * <p><b>This must always be a sub-image write, never an allocation.</b> An earlier version
	 * called {@code glTexImage2D} whenever the source rectangle happened to be the whole image
	 * starting at (0, 0), on the reasoning that a full-image write may as well allocate. That
	 * condition is a trap: it compares the rectangle against <i>this image's</i> size, not
	 * against the size of the texture being written into, and those are different things
	 * whenever an image is being packed into an atlas. {@code glTexImage2D} does not write into
	 * a texture, it <i>replaces</i> it - so the first glyph packed at (0, 0) silently resized
	 * the 256x256 font atlas to that glyph's 5x8, and every later glyph then failed with
	 * GL_INVALID_VALUE for being out of bounds. The same applied to the block atlas: the sprite
	 * that landed at (0, 0) shrank a 1024x1024 atlas to its own size.
	 *
	 * <p>The damage was total and invisible: in one startup, <b>every</b> one of 7,314
	 * {@code glTexSubImage2D} calls failed, and not one succeeded. Nothing threw, because GL
	 * reports errors by flag and nobody was reading the flag, so the only symptom was that
	 * every string of text rendered as a row of solid rectangles - the missing-glyph box being
	 * the one thing that did get into the atlas, by virtue of being written first.
	 *
	 * <p>Vanilla never allocates here either. Allocation is {@code TextureUtil.prepareImage}'s
	 * job and every caller of this method does that first - DynamicTexture, SimpleTexture,
	 * TextureAtlas and FontTexture all prepare the texture and then upload into it. Doing the
	 * same thing vanilla does is both correct and the smaller claim.
	 */
	public void upload(int level, int x, int y, int skipX, int skipY, int w, int h,
			boolean blur, boolean clamp, boolean mipmap, boolean autoClose) {
		checkOpen();
		try {
			// Vanilla sets the sampler state around every upload and this had been dropped,
			// which is not cosmetic: a texture then keeps whatever filter and wrap mode the
			// previously bound one left behind. Sharp pixel art came out smoothed because it
			// inherited GL_LINEAR, and the title panorama showed thin seams along the cube
			// edges because it inherited GL_REPEAT and sampled across to the opposite side of
			// each face.
			setFilter(blur, mipmap);

			IntBuffer buf = EagRuntime.allocateIntBuffer(w * h);
			for (int yy = 0; yy < h; ++yy) {
				buf.put(pixels, (skipY + yy) * width + skipX, w);
			}
			buf.flip();
			// GL_RGBA + GL_UNSIGNED_BYTE reads the buffer as R,G,B,A bytes, which is
			// exactly how these ints are laid out - see the class comment.
			EaglercraftGPU.glTexSubImage2D(3553 /* GL_TEXTURE_2D */, level, x, y, w, h,
					6408 /* GL_RGBA */, 5121 /* GL_UNSIGNED_BYTE */, buf);
			EagRuntime.freeIntBuffer(buf);

			if (clamp) {
				GlStateManager._texParameter(3553 /* GL_TEXTURE_2D */,
						10242 /* GL_TEXTURE_WRAP_S */, 33071 /* GL_CLAMP_TO_EDGE */);
				GlStateManager._texParameter(3553 /* GL_TEXTURE_2D */,
						10243 /* GL_TEXTURE_WRAP_T */, 33071 /* GL_CLAMP_TO_EDGE */);
			}
		} finally {
			if (autoClose) {
				close();
			}
		}
	}

	/**
	 * Reading a texture back into client memory needs glGetTexImage, which GLES and WebGL
	 * do not have. Vanilla uses this for screenshots and for the debug texture dump; the
	 * screenshot path in EaglercraftX reads the framebuffer instead.
	 */
	public void downloadTexture(int level, boolean opaque) {
		throw new UnsupportedOperationException(
				"Reading a texture back is not possible in WebGL; read the framebuffer");
	}

	/**
	 * STB TrueType rasterisation; see TrueTypeGlyphProvider. The parameter is STB's own
	 * struct type - declaring it as Object left vanilla's call site unresolved, because
	 * TeaVM matches on the exact descriptor.
	 */
	public void copyFromFont(org.lwjgl.stb.STBTTFontinfo fontInfo, int glyphIndex, int w, int h,
			float scaleX, float scaleY, float shiftX, float shiftY, int x, int y) {
		throw new UnsupportedOperationException(
				"TrueType glyphs are rasterised by the browser, not by STB");
	}

	/*
	 * Writing a PNG out. A page cannot write to a path, and EaglercraftX saves screenshots
	 * by handing the browser a download instead, so these are refused rather than silently
	 * doing nothing - a caller that thinks it saved a file would be worse.
	 */

	public void writeToFile(File file) throws IOException {
		throw new IOException("EaglercraftX cannot write files; screenshots are downloaded");
	}

	public void writeToFile(String path) throws IOException {
		throw new IOException("EaglercraftX cannot write files; screenshots are downloaded");
	}

	public void writeToFile(Path path) throws IOException {
		throw new IOException("EaglercraftX cannot write files; screenshots are downloaded");
	}

	/** Vanilla's leak tracker is a native-memory concern; this is a plain int[]. */
	public void untrack() {
	}

	@Override
	public void close() {
		pixels = null;
	}
}
