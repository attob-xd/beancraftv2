package org.teavm.classlib.javax.sound.sampled;

/**
 * Missing from TeaVM's class library. Named by AudioStream, which blaze3d's OggAudioStream
 * implements - the audio layer is not ported yet (EaglercraftX drives Web Audio instead of
 * OpenAL), but the type is in metadata, so it has to resolve. Enough of it exists to
 * describe a format; nothing decodes one.
 */
public class TAudioFormat {

	private final float sampleRate;
	private final int sampleSizeInBits;
	private final int channels;

	public TAudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed,
			boolean bigEndian) {
		this.sampleRate = sampleRate;
		this.sampleSizeInBits = sampleSizeInBits;
		this.channels = channels;
	}

	public float getSampleRate() {
		return sampleRate;
	}

	public int getSampleSizeInBits() {
		return sampleSizeInBits;
	}

	public int getChannels() {
		return channels;
	}
}
