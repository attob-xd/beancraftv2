package net.minecraft.locale;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StringDecomposer;

/**
 * Vanilla's class, with one change: the built-in en_us.json is read from the EPK rather than
 * from the classpath.
 *
 * Vanilla loads it in a static initialiser with
 * {@code Language.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")}. TeaVM
 * compiles ahead of time and there is no jar at runtime, so that returns null - and vanilla
 * hands the null straight to loadFromJson, which wraps it in an InputStreamReader and gives
 * it to gson. The failure is not a NullPointerException at the call; it is a TypeError raised
 * the first time gson pulls a byte, several frames deeper, inside JsonReader.peek(). The
 * catch clause here covers {@code JsonParseException | IOException} only, so it escaped, and
 * because this runs during the first resource reload it killed the client thread with
 * "Cannot read properties of null" and no Java frame naming this class.
 *
 * The assets are present - assets.epk carries assets/minecraft/lang/en_us.json, all 312 KB of
 * it - so the file is read from there, the same way VanillaPackResources reads everything
 * else. The path loses its leading slash because the EPK is keyed by plain path.
 *
 * A missing file is now reported rather than dereferenced: vanilla's own contract for
 * loadFromJson is that the stream is not null, so the check belongs here, at the point that
 * knows whether the resource exists.
 */
public abstract class Language {

	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new Gson();
	private static final Pattern UNSUPPORTED_FORMAT_PATTERN =
			Pattern.compile("%(\\d+\\$)?[\\d.]*[df]");
	public static final String DEFAULT = "en_us";

	/** No leading slash: the EPK is keyed by path, not by classpath resource name. */
	private static final String DEFAULT_LANGUAGE_FILE = "assets/minecraft/lang/en_us.json";

	private static volatile Language instance = loadDefault();

	private static Language loadDefault() {
		Builder<String, String> builder = ImmutableMap.builder();
		BiConsumer<String, String> sink = builder::put;
		byte[] bytes = EagRuntime.getResourceBytes(DEFAULT_LANGUAGE_FILE);
		if (bytes == null) {
			LOGGER.error("Couldn't read strings from {}: not present in the EPK",
					DEFAULT_LANGUAGE_FILE);
		} else {
			try (InputStream in = new ByteArrayInputStream(bytes)) {
				loadFromJson(in, sink);
			} catch (JsonParseException | IOException e) {
				LOGGER.error("Couldn't read strings from {}", DEFAULT_LANGUAGE_FILE, e);
			}
		}

		final ImmutableMap<String, String> translations = builder.build();
		return new Language() {
			@Override
			public String getOrDefault(String key) {
				return translations.getOrDefault(key, key);
			}

			@Override
			public boolean has(String key) {
				return translations.containsKey(key);
			}

			@Override
			public boolean isDefaultRightToLeft() {
				return false;
			}

			@Override
			public FormattedCharSequence getVisualOrder(FormattedText text) {
				return charSink -> text.visit(
						(style, string) -> StringDecomposer.iterateFormatted(string, style, charSink)
								? Optional.empty()
								: FormattedText.STOP_ITERATION,
						Style.EMPTY).isPresent();
			}
		};
	}

	public static void loadFromJson(InputStream stream, BiConsumer<String, String> output) {
		JsonObject json = GSON.fromJson(
				new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8),
				JsonObject.class);

		for (Entry<String, JsonElement> entry : json.entrySet()) {
			String value = rewriteUnsupportedFormats(
					GsonHelper.convertToString(entry.getValue(), entry.getKey()));
			output.accept(entry.getKey(), value);
		}
	}

	/**
	 * Vanilla writes this as {@code UNSUPPORTED_FORMAT_PATTERN.matcher(value).replaceAll("%$1s")}.
	 * It is spelled out here because TeaVM's Matcher cannot do that particular substitution.
	 *
	 * Group 1 is the optional positional prefix in {@code %(\d+\$)?[\d.]*[df]}, so it does not
	 * participate in a match against a plain "%d". The JDK appends nothing for a group that
	 * did not participate. TeaVM's processReplacement instead calls group(gr), gets null, and
	 * dereferences it - and a blanket {@code catch (Exception)} turns the resulting
	 * NullPointerException into {@code new IllegalArgumentException("")}. That empty message
	 * is the whole of what reaches the log.
	 *
	 * It is not hypothetical and it is not rare: en_us.json has four such matches, all of them
	 * bare "%d" in narration.suggestion and narration.suggestion.tooltip, and the first one
	 * killed the client thread inside this class's static initialiser.
	 *
	 * Fixing Matcher itself would mean replacing TeaVM's whole regex engine. It is not worth
	 * it: this is the only call in the whole of vanilla 1.18.2 that references a capture group
	 * from a replacement string, so this method is the entire blast radius.
	 *
	 * The behaviour below is the JDK's: emit "%", then group 1 if it participated, then "s".
	 */
	private static String rewriteUnsupportedFormats(String value) {
		Matcher matcher = UNSUPPORTED_FORMAT_PATTERN.matcher(value);
		StringBuilder out = null;
		int copied = 0;
		while (matcher.find()) {
			if (out == null) {
				out = new StringBuilder(value.length());
			}
			out.append(value, copied, matcher.start()).append('%');
			String positional = matcher.group(1);
			if (positional != null) {
				out.append(positional);
			}
			out.append('s');
			copied = matcher.end();
		}
		if (out == null) {
			return value;
		}
		return out.append(value, copied, value.length()).toString();
	}

	public static Language getInstance() {
		return instance;
	}

	public static void inject(Language language) {
		instance = language;
	}

	public abstract String getOrDefault(String key);

	public abstract boolean has(String key);

	public abstract boolean isDefaultRightToLeft();

	public abstract FormattedCharSequence getVisualOrder(FormattedText text);

	public List<FormattedCharSequence> getVisualOrder(List<FormattedText> texts) {
		return texts.stream().map(this::getVisualOrder)
				.collect(ImmutableList.toImmutableList());
	}
}
