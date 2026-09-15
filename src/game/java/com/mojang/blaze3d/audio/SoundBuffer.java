package com.mojang.blaze3d.audio;

import java.util.OptionalInt;

import javax.sound.sampled.AudioFormat;

import net.lax1dude.eaglercraft.internal.IAudioResource;

/**
 * Replaces blaze3d's decoded sound buffer.
 *
 * Vanilla holds PCM it decoded itself and uploads it to an OpenAL buffer, identified by an
 * integer name - which is what getAlBuffer and releaseAlBuffer trade in. There is no OpenAL
 * here and no integer to name: the browser decodes the file and hands back an AudioBuffer
 * object, which EaglercraftX wraps as an IAudioResource.
 *
 * So this holds the resource instead. The OptionalInt methods stay because they are the
 * shape Channel and SoundEngine were compiled against, and they return empty - honestly,
 * since there really is no AL buffer - while Channel reads the resource through getResource()
 * instead. Nothing in this build looks at the OptionalInt for anything but "is there one".
 *
 * Releasing is the browser's business. PlatformAudio keeps its own cache of decoded buffers
 * and evicts on a timer, so discarding here would only drop this object's reference.
 */
public class SoundBuffer {

	private final IAudioResource resource;
	private final AudioFormat format;

	public SoundBuffer(IAudioResource resource, AudioFormat format) {
		this.resource = resource;
		this.format = format;
	}

	public IAudioResource getResource() {
		return resource;
	}

	public AudioFormat getFormat() {
		return format;
	}

	/** There is no AL buffer; see the class comment. Channel uses getResource(). */
	OptionalInt getAlBuffer() {
		return OptionalInt.empty();
	}

	public void discardAlBuffer() {
	}

	public OptionalInt releaseAlBuffer() {
		return OptionalInt.empty();
	}
}
