/**
 * Times gson over every model and blockstate JSON the resource pack ships, on the desktop JVM.
 *
 *     java -cp "<gson>;<guava>" tools/TimeModelJson.java
 *
 * Not a correctness test - a yardstick. The client freezes at 100% CPU inside
 * Minecraft's constructor, and the suspect is ModelBakery's prepare(): because this port's
 * TForkJoinPool runs tasks inline, SimplePreparableReloadListener.reload's
 * `supplyAsync(this::prepare, backgroundExecutor)` executes synchronously rather than on a
 * background thread, so the whole reload happens before the game loop ever starts.
 *
 * The question that decides what to do next is whether that is slow-but-working or hung. This
 * measures the dominant cost on a JVM; multiply by a generous TeaVM slowdown to get an
 * expected upper bound. If the browser is still going well past that, it is not merely slow.
 */
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class TimeModelJson {

	public static void main(String[] args) throws IOException {
		Path assets = Path.of("desktopRuntime/resources/assets/minecraft");
		List<Path> files = new ArrayList<>();
		for (String dir : new String[] { "models", "blockstates" }) {
			Path root = assets.resolve(dir);
			if (!Files.isDirectory(root)) {
				continue;
			}
			try (Stream<Path> walk = Files.walk(root)) {
				for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					if (p.getFileName().toString().endsWith(".json")) {
						files.add(p);
					}
				}
			}
		}
		System.out.println("json files: " + files.size());
		if (files.isEmpty()) {
			System.out.println("nothing to measure");
			return;
		}

		// Read everything first so the timing is parsing, not disk.
		List<String> sources = new ArrayList<>(files.size());
		long bytes = 0;
		for (Path p : files) {
			String s = Files.readString(p, StandardCharsets.UTF_8);
			bytes += s.length();
			sources.add(s);
		}
		System.out.println("total size: " + (bytes / 1024) + " KiB");

		// Warm up, then measure.
		for (int i = 0; i < Math.min(200, sources.size()); ++i) {
			JsonParser.parseString(sources.get(i));
		}
		long start = System.nanoTime();
		int objects = 0;
		for (String s : sources) {
			if (JsonParser.parseString(s) instanceof JsonObject) {
				++objects;
			}
		}
		long ms = (System.nanoTime() - start) / 1_000_000L;
		System.out.println("parsed " + objects + " objects in " + ms + " ms on this JVM");
		System.out.println();
		System.out.println("expected browser cost at various TeaVM slowdowns:");
		for (int factor : new int[] { 5, 10, 20, 50, 100 }) {
			System.out.printf("   %3dx  ->  %.1f s%n", factor, ms * factor / 1000.0);
		}
		System.out.println();
		System.out.println("This counts parsing only. ModelBakery also resolves parents, "
				+ "bakes quads and stitches an atlas, so treat it as a floor, not a total.");
	}
}
