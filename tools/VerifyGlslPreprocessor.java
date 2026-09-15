/**
 * Runs the replaced GlslPreprocessor over every shipped shader, on the desktop JVM.
 *
 *     java -cp "build/classes/java/main;<libs>" tools/VerifyGlslPreprocessor.java
 *
 * Why: after commons-io's Charsets was fixed, the client stopped crashing in
 * GlslPreprocessor.processImports and started *spinning* there instead - 100% CPU, no further
 * log line, no frames, for minutes. The reload's own INFO lines ("Sound engine started",
 * "Created: NxNxN atlas") never appeared, so it never got past the shader preload in
 * Minecraft's constructor.
 *
 * That code is this port's own replacement of a vanilla class, so the first question is
 * whether the logic loops at all, or only loops under TeaVM's regex engine. This answers the
 * first half without a 20-minute build: it drives the real class the way ShaderInstance does -
 * same import resolution, same per-instance dedup of already-imported paths - over every
 * .vsh and .fsh in the resource pack, with a watchdog so a hang is reported rather than
 * inherited.
 *
 * If this passes and the browser still spins, the difference is TeaVM's java.util.regex, not
 * the preprocessor, and the fix belongs in the patterns rather than the logic.
 */
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;

public class VerifyGlslPreprocessor {

	static final Path SHADERS =
			Path.of("desktopRuntime/resources/assets/minecraft/shaders");

	/** Mirrors ShaderInstance's anonymous GlslPreprocessor, including its dedup. */
	static final class Importer extends GlslPreprocessor {
		private final Set<String> importedPaths = new HashSet<>();

		@Override
		public String applyImport(boolean quoted, String name) {
			String path = (quoted ? "shaders/include/" : "shaders/") + name;
			if (!this.importedPaths.add(path)) {
				return null;
			}
			Path file = SHADERS.resolve(path.substring("shaders/".length()));
			try {
				return Files.exists(file)
						? Files.readString(file, StandardCharsets.UTF_8)
						: null;
			} catch (IOException e) {
				return null;
			}
		}
	}

	public static void main(String[] args) throws Exception {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(SHADERS)) {
			for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
				String n = p.getFileName().toString();
				if (n.endsWith(".vsh") || n.endsWith(".fsh")) {
					files.add(p);
				}
			}
		}
		System.out.println("shaders to preprocess: " + files.size());
		if (files.isEmpty()) {
			System.out.println("FAILED: nothing to process - the test would prove nothing");
			System.exit(1);
		}

		ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "glsl");
			t.setDaemon(true);
			return t;
		});
		int ok = 0;
		int hung = 0;
		long slowestMs = 0;
		String slowest = null;
		for (Path file : files) {
			String source = Files.readString(file, StandardCharsets.UTF_8);
			final String name = SHADERS.relativize(file).toString().replace('\\', '/');
			Callable<List<String>> job = () -> new Importer().process(source);
			Future<List<String>> future = pool.submit(job);
			long start = System.nanoTime();
			try {
				List<String> parts = future.get(10, TimeUnit.SECONDS);
				long ms = (System.nanoTime() - start) / 1_000_000L;
				if (ms > slowestMs) {
					slowestMs = ms;
					slowest = name;
				}
				if (parts == null || parts.isEmpty()) {
					System.out.println("EMPTY  " + name);
				} else {
					++ok;
				}
			} catch (TimeoutException e) {
				future.cancel(true);
				++hung;
				System.out.println("HUNG   " + name + "  (no result in 10s)");
				// A hung single-thread pool cannot run the next job; stop here.
				break;
			} catch (Exception e) {
				System.out.println("THREW  " + name + ": " + rootCause(e));
			}
		}
		System.out.println(ok + " processed, " + hung + " hung");
		if (slowest != null) {
			System.out.println("slowest: " + slowest + " at " + slowestMs + " ms");
		}
		if (hung > 0) {
			System.out.println("FAILED - the preprocessor itself does not terminate");
			System.exit(1);
		}
		System.out.println("OK - GlslPreprocessor terminates on every shipped shader");
		System.exit(0);
	}

	static String rootCause(Throwable t) {
		while (t.getCause() != null) {
			t = t.getCause();
		}
		return t.toString();
	}
}
