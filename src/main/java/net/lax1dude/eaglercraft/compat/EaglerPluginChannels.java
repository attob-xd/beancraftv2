package net.lax1dude.eaglercraft.compat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageConstants;
import net.minecraft.resources.ResourceLocation;

/**
 * EaglercraftX's plugin-message channel names, in a form 1.18.2 will accept.
 *
 * <p>1.12.2 addressed a plugin message with a plain string, and EaglercraftX chose names in
 * the style of the day: {@code EAG|Skins-1.8}, {@code EAG|1.8}. 1.18.2 addresses one with a
 * {@link ResourceLocation}, whose path is restricted to {@code [a-z0-9/._-]} and which throws
 * {@code ResourceLocationException} from its constructor otherwise. Every one of those names
 * fails that check - on the capital letters and on the {@code |}.
 *
 * <p>This was not a theoretical problem. Both places that send an EaglercraftX message built
 * the packet as {@code new ResourceLocation(channel)}, so the first skin request the client
 * made - the moment another player came into view - threw out of the constructor rather than
 * sending anything.
 *
 * <p>The mapping is an explicit table rather than a computed transformation. A transformation
 * would have to be lossy (several illegal characters collapse to the same legal one), and a
 * lossy map cannot be reversed, which the receiving side needs to do. Writing the pairs out
 * also means the wire names are chosen deliberately and are visible in one place, which
 * matters because they are protocol: both ends of a connection must agree on them.
 *
 * <p>Names not in the table are not EaglercraftX's. {@link #toChannel} answers null for those,
 * which is how the receive hook tells an EaglercraftX message from an ordinary mod's.
 */
public final class EaglerPluginChannels {

	private static final String NAMESPACE = "eagler";

	private static final Map<String, ResourceLocation> TO_ID = new HashMap<>();
	private static final Map<ResourceLocation, String> TO_CHANNEL = new HashMap<>();

	static {
		map(GamePluginMessageConstants.V3_SKIN_CHANNEL, "skins_1_8");
		map(GamePluginMessageConstants.V3_CAPE_CHANNEL, "capes_1_8");
		map(GamePluginMessageConstants.V3_VOICE_CHANNEL, "voice_1_8");
		map(GamePluginMessageConstants.V3_UPDATE_CHANNEL, "updatecert_1_8");
		map(GamePluginMessageConstants.V3_FNAW_EN_CHANNEL, "fnawsen_1_8");
		map(GamePluginMessageConstants.V4_CHANNEL, "v1_8");
	}

	private static void map(String channel, String path) {
		ResourceLocation id = new ResourceLocation(NAMESPACE, path);
		TO_ID.put(channel, id);
		TO_CHANNEL.put(id, channel);
	}

	/** The ResourceLocation an EaglercraftX channel travels as, or null if it is not one. */
	public static ResourceLocation toId(String channel) {
		return channel == null ? null : TO_ID.get(channel);
	}

	/** The EaglercraftX channel a ResourceLocation carries, or null if it carries none. */
	public static String toChannel(ResourceLocation id) {
		return id == null ? null : TO_CHANNEL.get(id);
	}

	/** Every channel this build speaks, for logging and for tests. */
	public static Map<String, ResourceLocation> all() {
		return Collections.unmodifiableMap(TO_ID);
	}

	private EaglerPluginChannels() {
	}
}
