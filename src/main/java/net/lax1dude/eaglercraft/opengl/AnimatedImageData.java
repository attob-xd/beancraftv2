package net.lax1dude.eaglercraft.opengl;

/**
 * Fork addition: a decoded multi-frame image (animated GIF / APNG / WEBP).
 *
 * Deliberately a plain data holder. Platforms that cannot decode animation
 * simply return null from loadAnimatedImageFile and callers fall back to the
 * ordinary single frame path.
 */
public class AnimatedImageData {

	public final ImageData[] frames;

	/** Per frame display time in milliseconds, same length as frames. */
	public final int[] delaysMS;

	public AnimatedImageData(ImageData[] frames, int[] delaysMS) {
		this.frames = frames;
		this.delaysMS = delaysMS;
	}

	public int frameCount() {
		return frames.length;
	}

	public int totalDurationMS() {
		int total = 0;
		for(int i = 0; i < delaysMS.length; ++i) {
			total += delaysMS[i];
		}
		return total;
	}

}
