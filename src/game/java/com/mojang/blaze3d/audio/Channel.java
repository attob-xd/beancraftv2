package com.mojang.blaze3d.audio;

import net.lax1dude.eaglercraft.blaze3d.BrowserAudioStream;
import net.lax1dude.eaglercraft.internal.IAudioHandle;
import net.lax1dude.eaglercraft.internal.IAudioResource;
import net.lax1dude.eaglercraft.internal.PlatformAudio;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.phys.Vec3;

/**
 * Replaces blaze3d's OpenAL source.
 *
 * The two models differ in one way that shapes this whole class. OpenAL lets you create a
 * silent source, configure it, and then say play; the browser does not - a Web Audio source
 * node is created and started in one step and cannot be reconfigured into existence
 * afterwards. EaglercraftX's PlatformAudio follows the browser, so beginPlayback both starts
 * the sound and takes its position, volume and pitch.
 *
 * So a Channel collects its settings and does nothing with them until play(). After that the
 * handle exists and every setter forwards to it live, which is what SoundEngine expects when
 * it moves a sound with the player or fades one out. Settings applied before play() are not
 * lost, they are simply pending.
 *
 * Streaming is flattened. blaze3d queues small buffers for music and records because it
 * decodes Vorbis incrementally; the browser decodes whole files, so attachBufferStream takes
 * the finished resource off the stream and plays it like any other sound, and updateStream()
 * has nothing to pump. See BrowserAudioStream.
 *
 * Attenuation is the browser's. Web Audio's PannerNode applies its own distance rolloff, and
 * PlatformAudio configures it; there is no way to swap in OpenAL's linear model or to turn
 * attenuation off per source, so disableAttenuation and linearAttenuation are recorded and
 * used to choose between positional and non-positional playback, which is the part of their
 * meaning that can be honoured.
 */
public class Channel {

	private IAudioResource resource;
	private IAudioHandle handle;

	private float x;
	private float y;
	private float z;
	private float volume = 1.0F;
	private float pitch = 1.0F;
	private boolean looping;
	/**
	 * Vanilla sets these separately and in this order - the attenuation mode first, then
	 * relativeness - so they cannot share one field: setRelative(false) would undo a
	 * preceding disableAttenuation(). That combination is real (thunder is a world sound
	 * with attenuation NONE) and collapsing it would make distant thunder inaudible.
	 */
	private boolean relative;
	private boolean attenuated = true;
	private boolean paused;
	private boolean stopped;

	/**
	 * A sound is played without a position when it is relative to the listener, or when
	 * vanilla asked for no distance falloff at all - the browser's panner always attenuates,
	 * so "everywhere at once" is the closest honest rendering of both.
	 */
	private boolean nonPositional() {
		return relative || !attenuated;
	}

	static Channel create() {
		return new Channel();
	}

	Channel() {
	}

	public void play() {
		if (resource == null || handle != null) {
			return;
		}
		stopped = false;
		if (nonPositional()) {
			handle = PlatformAudio.beginPlaybackStatic(resource, volume, pitch, looping);
		} else {
			handle = PlatformAudio.beginPlayback(resource, x, y, z, volume, pitch, looping);
		}
		if (handle == null) {
			// The browser refused to start it - most often because the page has not had a
			// user gesture yet. Treating it as finished lets SoundEngine reclaim the channel
			// instead of waiting on a sound that will never end.
			stopped = true;
		}
	}

	public void pause() {
		paused = true;
		if (handle != null) {
			handle.pause(true);
		}
	}

	public void unpause() {
		paused = false;
		if (handle != null) {
			handle.pause(false);
		}
	}

	public void stop() {
		stopped = true;
		if (handle != null) {
			handle.end();
			handle = null;
		}
	}

	public void destroy() {
		stop();
		resource = null;
	}

	public boolean playing() {
		return !stopped && !paused && handle != null && !handle.shouldFree();
	}

	public boolean stopped() {
		return stopped || handle == null || handle.shouldFree();
	}

	public void setSelfPosition(Vec3 position) {
		this.x = (float) position.x;
		this.y = (float) position.y;
		this.z = (float) position.z;
		if (handle != null) {
			handle.move(x, y, z);
		}
	}

	public void setPitch(float pitch) {
		this.pitch = pitch;
		if (handle != null) {
			handle.pitch(pitch);
		}
	}

	public void setVolume(float volume) {
		this.volume = volume;
		if (handle != null) {
			handle.gain(volume);
		}
	}

	public void setLooping(boolean looping) {
		this.looping = looping;
		if (handle != null) {
			handle.repeat(looping);
		}
	}

	/** See the class comment: the browser owns the distance model. */
	public void disableAttenuation() {
		this.attenuated = false;
	}

	public void linearAttenuation(float distance) {
		this.attenuated = true;
	}

	public void setRelative(boolean relative) {
		this.relative = relative;
	}

	public void attachStaticBuffer(SoundBuffer buffer) {
		this.resource = buffer == null ? null : buffer.getResource();
	}

	public void attachBufferStream(AudioStream stream) {
		if (stream instanceof BrowserAudioStream) {
			BrowserAudioStream browser = (BrowserAudioStream) stream;
			this.resource = browser.getResource();
			this.looping = browser.isLooping();
		} else {
			// Nothing else produces an AudioStream in this build; if something starts to,
			// this says so rather than playing silence.
			throw new UnsupportedOperationException(
					"Cannot play " + stream.getClass().getName()
							+ ": browser audio decodes whole files, not streams");
		}
	}

	/** Nothing to pump; see the class comment. */
	public void updateStream() {
	}
}
