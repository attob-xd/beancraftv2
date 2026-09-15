package net.lax1dude.eaglercraft.profile;

import net.lax1dude.eaglercraft.opengl.ImageData;

/**
 * Fork addition: composes an arbitrary image into a cape texture.
 *
 * The stock cape is a 32x32 canvas holding the vanilla cape UV regions, with the
 * visible back panel at (1,1) sized 10x16. Rather than inventing new UVs, this
 * keeps that exact layout but renders it at CAPE_SCALE times the resolution, so
 * cape UVs stay correct while the artwork gets far more detail than 10x16.
 *
 * The source image is fitted to the panel, then the wearer's own scale and
 * offset are applied on top, which is what lets them move and zoom the picture.
 */
public class HDCapeComposer {

	/** Upscale factor over the stock 32x32 cape layout. 8 gives a 256x256 texture. */
	public static final int CAPE_SCALE = 8;
	public static final int TEX_SIZE = 32 * CAPE_SCALE;

	/** Visible back panel, in stock 32x32 cape layout coordinates. */
	public static final int PANEL_X = 1;
	public static final int PANEL_Y = 1;
	public static final int PANEL_W = 10;
	public static final int PANEL_H = 16;

	/** Fixed point unit for the scale field: SCALE_ONE == 1.0x. */
	public static final int SCALE_ONE = 256;

	/**
	 * @param offsetX/offsetY in 1/16 cape-pixel units, relative to panel centre
	 * @param scale fixed point, SCALE_ONE == fit-to-panel
	 * @return ARGB bytes, TEX_SIZE x TEX_SIZE, fully transparent where unpainted
	 */
	public static byte[] compose(ImageData src, int offsetX, int offsetY, int scale) {
		return compose(src, offsetX, offsetY, scale, CAPE_SCALE);
	}

	/** As above, but at an explicit upscale factor. capeScale 1 gives the stock 32x32 layout. */
	public static byte[] compose(ImageData src, int offsetX, int offsetY, int scale, int capeScale) {
		int texSize = 32 * capeScale;
		byte[] out = new byte[texSize * texSize * 4];
		if(src == null || src.width <= 0 || src.height <= 0) {
			return out;
		}
		if(scale <= 0) {
			scale = SCALE_ONE;
		}

		int px0 = PANEL_X * capeScale;
		int py0 = PANEL_Y * capeScale;
		int pw = PANEL_W * capeScale;
		int ph = PANEL_H * capeScale;

		// COVER, not contain: fill the whole cape and crop the overflow. Fitting the
		// image inside the panel instead left big empty bands, because the cape is a
		// tall 10:16 panel and most images are wider than that. Zoom out to see more.
		float fit = Math.max((float) pw / src.width, (float) ph / src.height);
		float s = fit * scale / SCALE_ONE;
		if(s <= 0.0f) {
			return out;
		}

		float ox = offsetX * capeScale / 16.0f;
		float oy = offsetY * capeScale / 16.0f;

		// The visible outer face.
		paintPanel(out, texSize, src, px0, py0, pw, ph, ox, oy, s);
		// The inner lining, at PANEL_X + PANEL_W + 1 in stock cape layout coordinates.
		// Without this the cape is see-through whenever it swings away from the body.
		paintPanel(out, texSize, src, (PANEL_X + PANEL_W + 1) * capeScale, py0, pw, ph, ox, oy, s);
		// The box also has 1px edge strips around the faces. Left transparent they
		// show up as a ragged see-through rim, so bleed the nearest painted pixel out.
		fillTransparentEdges(out, texSize, capeScale);

		return out;
	}

	private static void paintPanel(byte[] out, int texSize, ImageData src, int px0, int py0, int pw, int ph,
			float ox, float oy, float s) {
		float originX = px0 + pw * 0.5f + ox - src.width * s * 0.5f;
		float originY = py0 + ph * 0.5f + oy - src.height * s * 0.5f;

		int x0 = Math.max(px0, (int) Math.floor(originX));
		int x1 = Math.min(px0 + pw, (int) Math.ceil(originX + src.width * s));
		int y0 = Math.max(py0, (int) Math.floor(originY));
		int y1 = Math.min(py0 + ph, (int) Math.ceil(originY + src.height * s));

		for(int y = y0; y < y1; ++y) {
			int sy = (int) ((y + 0.5f - originY) / s);
			if(sy < 0 || sy >= src.height) {
				continue;
			}
			for(int x = x0; x < x1; ++x) {
				int sx = (int) ((x + 0.5f - originX) / s);
				if(sx < 0 || sx >= src.width) {
					continue;
				}
				int argb = src.pixels[sy * src.width + sx];
				int i = (y * texSize + x) << 2;
				out[i] = (byte) (argb >>> 24);
				out[i + 1] = (byte) (argb >>> 16);
				out[i + 2] = (byte) (argb >>> 8);
				out[i + 3] = (byte) argb;
			}
		}
	}

	/**
	 * Bleeds painted pixels outward into any still-transparent pixel inside the
	 * cape's used region, which fills the thin edge strips of the box.
	 */
	private static void fillTransparentEdges(byte[] out, int texSize, int capeScale) {
		int usedW = 23 * capeScale;
		int usedH = 17 * capeScale;
		if(usedW > texSize) {
			usedW = texSize;
		}
		if(usedH > texSize) {
			usedH = texSize;
		}
		for(int y = 0; y < usedH; ++y) {
			for(int x = 0; x < usedW; ++x) {
				int i = (y * texSize + x) << 2;
				if(out[i] != 0) {
					continue;
				}
				for(int r = 1; r <= capeScale && out[i] == 0; ++r) {
					int[][] probes = new int[][] { { x - r, y }, { x + r, y }, { x, y - r }, { x, y + r } };
					for(int k = 0; k < probes.length; ++k) {
						int nx = probes[k][0];
						int ny = probes[k][1];
						if(nx < 0 || ny < 0 || nx >= usedW || ny >= usedH) {
							continue;
						}
						int j = (ny * texSize + nx) << 2;
						if(out[j] != 0) {
							out[i] = out[j];
							out[i + 1] = out[j + 1];
							out[i + 2] = out[j + 2];
							out[i + 3] = out[j + 3];
							break;
						}
					}
				}
			}
		}
	}


	/**
	 * Renders the same composition down to the stock 1173 byte 23x17 RGB cape.
	 * This is the fallback every unpatched server and stock client still sees, so
	 * an HD cape degrades to a recognisable low-res version instead of nothing.
	 */
	public static byte[] composeLegacy(ImageData src, int offsetX, int offsetY, int scale) {
		byte[] argb = compose(src, offsetX, offsetY, scale, 1);
		ImageData img = new ImageData(32, 32, EaglerSkinTexture.convertToInt(argb), true);
		byte[] out = new byte[1173];
		SkinConverter.convertCape32x32RGBAto23x17RGB(img, out);
		return out;
	}

}
