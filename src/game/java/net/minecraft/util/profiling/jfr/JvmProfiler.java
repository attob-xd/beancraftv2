package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Vanilla's interface, with one line changed: which implementation {@link #INSTANCE} is.
 *
 * <p>Vanilla decides that by asking whether the JVM has Flight Recorder:
 *
 * <pre>Runtime.class.getModule().getLayer().findModule("jdk.jfr").isPresent()</pre>
 *
 * <p>which needs {@code Class.getModule()}. TeaVM has no module system and no such method, so
 * that expression throws {@code NoSuchMethodError} from this interface's static initialiser -
 * and because the field is read from {@code Commands}' constructor, it took the client down
 * the moment a world started loading, several frames into {@code Minecraft.doLoadLevel}.
 *
 * <p>The answer the check is reaching for is knowable here without asking: a browser has no
 * JVM, so it has no Flight Recorder, and the module could never be present. Naming
 * {@code NoOpProfiler} directly is the same answer the check would have given, arrived at
 * without a method that does not exist.
 *
 * <p>Everything else - the interface, the nested no-op implementation, its logging - is
 * vanilla's, unchanged, so every caller behaves exactly as it does on a desktop client running
 * a JVM without JFR.
 */
public interface JvmProfiler {
	/** See the class comment: no JVM, therefore no Flight Recorder, therefore the no-op. */
	JvmProfiler INSTANCE = new JvmProfiler.NoOpProfiler();

	boolean start(Environment var1);

	Path stop();

	boolean isRunning();

	boolean isAvailable();

	void onServerTick(float var1);

	void onPacketReceived(int var1, int var2, SocketAddress var3, int var4);

	void onPacketSent(int var1, int var2, SocketAddress var3, int var4);

	@Nullable
	ProfiledDuration onWorldLoadedStarted();

	@Nullable
	ProfiledDuration onChunkGenerate(ChunkPos var1, ResourceKey<Level> var2, String var3);

	public static class NoOpProfiler implements JvmProfiler {
		private static final Logger LOGGER = LogUtils.getLogger();
		static final ProfiledDuration noOpCommit = () -> {
		};

		@Override
		public boolean start(Environment var1) {
			LOGGER.warn("Attempted to start Flight Recorder, but it's not supported on this JVM");
			return false;
		}

		@Override
		public Path stop() {
			throw new IllegalStateException(
					"Attempted to stop Flight Recorder, but it's not supported on this JVM");
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
		public void onPacketReceived(int var1, int var2, SocketAddress var3, int var4) {
		}

		@Override
		public void onPacketSent(int var1, int var2, SocketAddress var3, int var4) {
		}

		@Override
		public void onServerTick(float var1) {
		}

		@Override
		public ProfiledDuration onWorldLoadedStarted() {
			return noOpCommit;
		}

		@Nullable
		@Override
		public ProfiledDuration onChunkGenerate(ChunkPos var1, ResourceKey<Level> var2, String var3) {
			return null;
		}
	}
}
