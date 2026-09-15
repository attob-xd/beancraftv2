package net.lax1dude.eaglercraft.sp.server;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.ProfileLookupCallback;

import net.lax1dude.eaglercraft.EaglercraftUUID;

/**
 * Offline profile lookups for the integrated server.
 *
 * <p>The real {@code YggdrasilGameProfileRepository} talks to Mojang's session servers,
 * which the browser cannot reach and singleplayer does not need. Names resolve to the
 * same offline UUID Minecraft derives for an offline player, so a world keeps recognising
 * its player across sessions.
 */
public class EaglerOfflineProfileRepository implements GameProfileRepository {

	@Override
	public void findProfilesByNames(String[] names, Agent agent, ProfileLookupCallback callback) {
		for (String name : names) {
			callback.onProfileLookupSucceeded(new GameProfile(offlineId(name), name));
		}
	}

	/** The same derivation Minecraft uses for offline players. */
	public static java.util.UUID offlineId(String name) {
		return EaglercraftUUID
				.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8))
				.toJavaUUID();
	}
}
