package net.minecraft.util.profiling.jfr;

import java.net.SocketAddress;
import java.nio.file.Path;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Replaces the Java Flight Recorder profiler. JFR is a JVM feature - jdk.jfr.Recording,
 * its event types and its chunk files - with no counterpart in a browser, and reaching for
 * it pulled jdk.jfr.internal's reflective type library into the dependency graph.
 *
 * isAvailable() answers false, which is the same answer vanilla gives on a JVM built
 * without JFR, so `/jfr start` reports that profiling is unavailable rather than failing.
 */
public class JfrProfiler implements JvmProfiler {

	public static final String ROOT_CATEGORY = "Minecraft";
	public static final String WORLD_GEN_CATEGORY = "World Generation";
	public static final String TICK_CATEGORY = "Ticking";
	public static final String NETWORK_CATEGORY = "Network";

	private static final JfrProfiler INSTANCE = new JfrProfiler();

	public static JfrProfiler getInstance() {
		return INSTANCE;
	}

	@Override
	public boolean start(Environment environment) {
		return false;
	}

	@Override
	public Path stop() {
		return null;
	}

	@Override
	public boolean isRunning() {
		return false;
	}

	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public void onServerTick(float tickTime) {
	}

	@Override
	public void onPacketReceived(int protocolId, int packetId, SocketAddress remote, int bytes) {
	}

	@Override
	public void onPacketSent(int protocolId, int packetId, SocketAddress remote, int bytes) {
	}

	@Override
	public ProfiledDuration onWorldLoadedStarted() {
		return null;
	}

	@Override
	public ProfiledDuration onChunkGenerate(ChunkPos pos, ResourceKey<Level> level, String name) {
		return null;
	}
}
