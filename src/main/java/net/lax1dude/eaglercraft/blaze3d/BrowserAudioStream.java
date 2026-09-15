package net.lax1dude.eaglercraft.blaze3d;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.sound.sampled.AudioFormat;

import net.lax1dude.eaglercraft.internal.IAudioResource;
import net.minecraft.client.sounds.AudioStream;

/**
 * An AudioStream that is not actually a stream.
 *
 * blaze3d streams long sounds - music, records - because it decodes Vorbis itself, a frame at
 * a time, and feeds OpenAL a rolling queue of small buffers. The browser does not work that
 * way: decodeAudioData takes the whole encoded file and hands back one finished AudioBuffer,
 * which the page then plays as a unit. There is nothing to pump.
 *
 * So this carries the decoded resource and reports an empty read. Channel recognises the type
 * and plays the resource whole rather than trying to queue it, and updateStream() has nothing
 * to do. The cost is that a long track is fully decoded before it starts instead of streaming
 * in - more memory and a slower first note - which is the trade the browser's audio API makes
 * for us, not a shortcut taken here.
 */
public class BrowserAudioStream implements AudioStream {

	/**
	 * blaze3d only reads this to size its streaming buffers, which are not used on this path.
	 * The values describe what the browser decodes to - 16-bit stereo at 44.1 kHz - so that
	 * anything logging the format sees something true rather than zeroes.
	 */
	private static final AudioFormat FORMAT = new AudioFormat(44100.0F, 16, 2, true, false);

	private final IAudioResource resource;
	private final boolean looping;

	public BrowserAudioStream(IAudioResource resource, boolean looping) {
		this.resource = resource;
		this.looping = looping;
	}

	public IAudioResource getResource() {
		return resource;
	}

	public boolean isLooping() {
		return looping;
	}

	@Override
	public AudioFormat getFormat() {
		return FORMAT;
	}

	/** Nothing to hand back: the browser owns the samples. See the class comment. */
	@Override
	public ByteBuffer read(int size) throws IOException {
		return ByteBuffer.allocate(0);
	}

	@Override
	public void close() throws IOException {
	}
}
