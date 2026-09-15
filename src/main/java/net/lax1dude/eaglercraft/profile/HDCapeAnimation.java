package net.lax1dude.eaglercraft.profile;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.opengl.AnimatedImageData;
import net.lax1dude.eaglercraft.opengl.ImageData;

/**
 * Fork addition: drives an animated (GIF) cape.
 *
 * Every frame is composed once up front into the cape layout, then swapped into
 * the live texture as time passes. Advancing is done lazily from the render path
 * rather than from a tick registry, so a cape nobody can see costs nothing.
 *
 * Animated capes are composed at a lower resolution than still ones on purpose:
 * a 256x256 frame is 256 KB, so a long GIF would otherwise eat tens of megabytes.
 */
public class HDCapeAnimation {

	/** Lower than HDCapeComposer.CAPE_SCALE to keep multi frame memory sane. */
	public static final int ANIM_CAPE_SCALE = 4;
	public static final int ANIM_TEX_SIZE = 32 * ANIM_CAPE_SCALE;

	public static final int MAX_ANIM_FRAMES = 32;

	private final byte[][] frames;
	private final int[] delaysMS;
	private final int totalMS;
	private final long startMS;
	private int lastFrame = -1;

	private HDCapeAnimation(byte[][] frames, int[] delaysMS, int totalMS) {
		this.frames = frames;
		this.delaysMS = delaysMS;
		this.totalMS = totalMS;
		this.startMS = EagRuntime.steadyTimeMillis();
	}

	/**
	 * @return an animation, or null if the image is not animated or cannot be
	 *         decoded, in which case the caller should use the still image path.
	 */
	public static HDCapeAnimation create(byte[] imageData, int offsetX, int offsetY, int scale) {
		if(imageData == null || imageData.length == 0) {
			return null;
		}
		AnimatedImageData anim;
		try {
			anim = ImageData.loadAnimatedImageFile(imageData, sniffMime(imageData));
		}catch(Throwable t) {
			return null;
		}
		if(anim == null || anim.frameCount() < 2) {
			return null;
		}
		int count = Math.min(anim.frameCount(), MAX_ANIM_FRAMES);
		byte[][] composed = new byte[count][];
		int[] delays = new int[count];
		int total = 0;
		for(int i = 0; i < count; ++i) {
			composed[i] = HDCapeComposer.compose(anim.frames[i], offsetX, offsetY, scale, ANIM_CAPE_SCALE);
			delays[i] = anim.delaysMS[i] > 0 ? anim.delaysMS[i] : 100;
			total += delays[i];
		}
		if(total <= 0) {
			return null;
		}
		return new HDCapeAnimation(composed, delays, total);
	}

	/** Swaps the current frame into the texture. Cheap when the frame has not changed. */
	public void tick(EaglerSkinTexture texture) {
		if(texture == null || totalMS <= 0) {
			return;
		}
		int elapsed = (int) ((EagRuntime.steadyTimeMillis() - startMS) % totalMS);
		int index = 0;
		int acc = 0;
		for(int i = 0; i < delaysMS.length; ++i) {
			acc += delaysMS[i];
			if(elapsed < acc) {
				index = i;
				break;
			}
		}
		if(index != lastFrame) {
			lastFrame = index;
			texture.copyPixelsIn(frames[index]);
		}
	}

	/**
	 * The receiving side has no filename to go on, so the container is identified
	 * from its magic bytes. ImageDecoder needs an accurate type.
	 */
	public static String sniffMime(byte[] d) {
		if(d.length >= 4 && d[0] == (byte) 'G' && d[1] == (byte) 'I' && d[2] == (byte) 'F') {
			return "image/gif";
		}
		if(d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == (byte) 'P' && d[2] == (byte) 'N' && d[3] == (byte) 'G') {
			return "image/png";
		}
		if(d.length >= 12 && d[0] == (byte) 'R' && d[1] == (byte) 'I' && d[2] == (byte) 'F' && d[3] == (byte) 'F'
				&& d[8] == (byte) 'W' && d[9] == (byte) 'E' && d[10] == (byte) 'B' && d[11] == (byte) 'P') {
			return "image/webp";
		}
		if(d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) {
			return "image/jpeg";
		}
		return "image/png";
	}

}
