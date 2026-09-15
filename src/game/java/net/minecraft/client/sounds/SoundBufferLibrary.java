package net.minecraft.client.sounds;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.sound.sampled.AudioFormat;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.audio.SoundBuffer;

import net.lax1dude.eaglercraft.blaze3d.BrowserAudioStream;
import net.lax1dude.eaglercraft.internal.IAudioResource;
import net.lax1dude.eaglercraft.internal.PlatformAudio;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Replaces vanilla's sound loader.
 *
 * Vanilla decodes Vorbis itself, with STB, and hands the resulting PCM to OpenAL. Neither
 * half exists here, and the interesting point is that the browser already does both: give
 * decodeAudioData the encoded file and it returns a finished AudioBuffer. EaglercraftX wraps
 * that as PlatformAudio.loadAudioDataNew, which also carries lax1dude's JOrbis fallback for
 * browsers that cannot decode Ogg natively - Safari, mainly - so replacing this class gets
 * that path for free rather than reimplementing a Vorbis decoder in Java.
 *
 * The consequence for callers is small and is confined to two places. getCompleteBuffer hands
 * back a SoundBuffer holding a resource rather than PCM, and getStream hands back a
 * BrowserAudioStream that is not really a stream - Channel understands both. Everything else
 * about the shape is vanilla's: the same futures, the same cache keyed by ResourceLocation,
 * the same preload.
 *
 * The futures complete immediately. Every executor in this build runs its work inline (see
 * the class library shims), so "load this off the main thread" means "load it now"; the page
 * pauses while a sound decodes instead of staying responsive, which is the same trade the
 * rest of the port makes.
 */
public class SoundBufferLibrary {

	private final ResourceManager resourceManager;
	private final Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache = Maps.newHashMap();

	/**
	 * What the browser decodes to. Nothing on this path reads it for anything but logging and
	 * buffer sizing that no longer happens, but it should not be zeroes.
	 */
	private static final AudioFormat FORMAT = new AudioFormat(44100.0F, 16, 2, true, false);

	public SoundBufferLibrary(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	/**
	 * Deliberately not computeIfAbsent. The browser's decodeAudioData is asynchronous, and
	 * PlatformAudio bridges it with an @Async method - so load() below suspends this thread
	 * and the page runs other frames while it waits. Suspending inside computeIfAbsent means
	 * a second frame can call this method and mutate the map while the first call is still
	 * inside the mapping function, which is exactly the re-entrancy HashMap does not allow.
	 * Reading, loading, then writing keeps the map untouched across the suspension point.
	 */
	public CompletableFuture<SoundBuffer> getCompleteBuffer(ResourceLocation location) {
		CompletableFuture<SoundBuffer> existing = cache.get(location);
		if (existing != null) {
			return existing;
		}
		CompletableFuture<SoundBuffer> future = new CompletableFuture<>();
		try {
			IAudioResource resource = load(location, true);
			if (resource == null) {
				future.completeExceptionally(new IOException("Could not decode sound: " + location));
			} else {
				future.complete(new SoundBuffer(resource, FORMAT));
			}
		} catch (Throwable t) {
			future.completeExceptionally(t);
		}
		// Another frame may have finished the same sound while this one was suspended; keep
		// whichever landed first so callers holding either future see the same buffer.
		CompletableFuture<SoundBuffer> raced = cache.putIfAbsent(location, future);
		return raced != null ? raced : future;
	}

	public CompletableFuture<AudioStream> getStream(ResourceLocation location, boolean looping) {
		CompletableFuture<AudioStream> future = new CompletableFuture<>();
		try {
			// Streamed sounds are not cached: they are music and records, played once and
			// large, and holding them would keep several minutes of decoded audio alive.
			IAudioResource resource = load(location, false);
			if (resource == null) {
				future.completeExceptionally(new IOException("Could not decode sound: " + location));
			} else {
				future.complete(new BrowserAudioStream(resource, looping));
			}
		} catch (Throwable t) {
			future.completeExceptionally(t);
		}
		return future;
	}

	/**
	 * Reads the file out of the resource pack and lets the browser decode it. The pack path
	 * doubles as PlatformAudio's cache key, which is why it is built the same way vanilla
	 * builds it.
	 */
	private IAudioResource load(ResourceLocation location, boolean holdInCache)
			throws IOException {
		String key = location.getNamespace() + ":" + location.getPath();
		return PlatformAudio.loadAudioDataNew(key, holdInCache, filename -> {
			try (Resource resource = resourceManager.getResource(location);
					InputStream in = resource.getInputStream()) {
				return readFully(in);
			} catch (IOException e) {
				return null;
			}
		});
	}

	private static byte[] readFully(InputStream in) throws IOException {
		byte[] buffer = new byte[16384];
		int len = 0;
		while (true) {
			if (len == buffer.length) {
				byte[] bigger = new byte[buffer.length << 1];
				System.arraycopy(buffer, 0, bigger, 0, len);
				buffer = bigger;
			}
			int read = in.read(buffer, len, buffer.length - len);
			if (read < 0) {
				break;
			}
			len += read;
		}
		if (len == buffer.length) {
			return buffer;
		}
		byte[] out = new byte[len];
		System.arraycopy(buffer, 0, out, 0, len);
		return out;
	}

	public void clear() {
		cache.clear();
		PlatformAudio.clearAudioCache();
	}

	public CompletableFuture<?> preload(Collection<Sound> sounds) {
		List<CompletableFuture<SoundBuffer>> futures = sounds.stream()
				.map(sound -> getCompleteBuffer(sound.getPath()))
				.collect(Collectors.toList());
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}
}
