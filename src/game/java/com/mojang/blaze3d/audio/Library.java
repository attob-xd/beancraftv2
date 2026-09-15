package com.mojang.blaze3d.audio;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.lax1dude.eaglercraft.internal.PlatformAudio;

/**
 * Replaces blaze3d's OpenAL device and channel pool.
 *
 * This is on the boot path, not in a lazy corner: SoundEngine.loadLibrary() runs during the
 * first resource reload, inside Minecraft's constructor, and vanilla's init() opens a device
 * through ALC10. There is no OpenAL in a page - EaglercraftX has Web Audio instead, already
 * initialised by the time the client starts, which is what "Initializing sound engine..." in
 * the boot log is.
 *
 * So init() checks that the audio context came up and otherwise throws. Throwing is the
 * supported way to fail here: SoundEngine catches RuntimeException, logs "Error starting
 * SoundSystem. Turning off sounds and music" and runs on in silence. What it does not survive
 * is an Error, which is what an unimplemented LWJGL native would have raised - so this class
 * is also what keeps a missing device from taking the whole client down.
 *
 * The channel pools are vanilla's, kept because SoundEngine depends on their limits: it asks
 * for a channel and expects null when the pool is exhausted, which is how it decides to drop
 * a sound rather than queue it forever. The counts are vanilla's own defaults.
 */
public class Library {

	/** Vanilla's two channel pools; the type is named in SoundEngine's signatures. */
	public static enum Pool {
		STATIC,
		STREAMING
	}

	/*
	 * Vanilla sizes these from the OpenAL device's mono-source count, clamped to 30 static
	 * and 8 streaming. There is nothing to query here and the browser imposes no comparable
	 * limit, so the clamps are used directly - they are what vanilla runs with on virtually
	 * every real device anyway, and keeping them keeps SoundEngine's behaviour identical.
	 */
	private static final int MAX_STATIC_CHANNELS = 30;
	private static final int MAX_STREAMING_CHANNELS = 8;

	private final Listener listener = new Listener();
	private final Set<Channel> staticChannels = new HashSet<>();
	private final Set<Channel> streamingChannels = new HashSet<>();
	private boolean open;

	public void init(String deviceName) {
		if (!PlatformAudio.available()) {
			throw new IllegalStateException(
					"The browser did not give this page an audio context, so there is no"
							+ " device to open");
		}
		// The browser chooses the output device and does not let a page pick one, so a
		// non-empty deviceName from the options file cannot be honoured. It is ignored
		// rather than treated as an error, which is what vanilla does with a device that
		// has gone away.
		open = true;
	}

	public void cleanup() {
		for (Channel channel : new HashSet<>(staticChannels)) {
			channel.destroy();
		}
		for (Channel channel : new HashSet<>(streamingChannels)) {
			channel.destroy();
		}
		staticChannels.clear();
		streamingChannels.clear();
		open = false;
	}

	public Listener getListener() {
		return listener;
	}

	/** Null when the pool is full: SoundEngine reads that as "drop this sound". */
	public Channel acquireChannel(Pool pool) {
		if (!open) {
			return null;
		}
		Set<Channel> set = pool == Pool.STREAMING ? streamingChannels : staticChannels;
		int max = pool == Pool.STREAMING ? MAX_STREAMING_CHANNELS : MAX_STATIC_CHANNELS;
		if (set.size() >= max) {
			return null;
		}
		Channel channel = Channel.create();
		set.add(channel);
		return channel;
	}

	public void releaseChannel(Channel channel) {
		if (channel == null) {
			return;
		}
		channel.destroy();
		staticChannels.remove(channel);
		streamingChannels.remove(channel);
	}

	public String getDebugString() {
		return String.format("Sounds: %d/%d + %d/%d", staticChannels.size(), MAX_STATIC_CHANNELS,
				streamingChannels.size(), MAX_STREAMING_CHANNELS);
	}

	/**
	 * The browser picks the output device and does not tell the page which, so there is no
	 * list to offer and nothing to notice changing. Vanilla's sound-device option is left
	 * with only its "System Default" entry, which is the truth here.
	 */
	public List<String> getAvailableSoundDevices() {
		return Collections.emptyList();
	}

	public String getCurrentDeviceName() {
		return "<browser default>";
	}

	public static String getDefaultDeviceName() {
		return "<browser default>";
	}

	public synchronized boolean hasDefaultDeviceChanged() {
		return false;
	}

	public boolean isCurrentDeviceDisconnected() {
		return false;
	}
}
