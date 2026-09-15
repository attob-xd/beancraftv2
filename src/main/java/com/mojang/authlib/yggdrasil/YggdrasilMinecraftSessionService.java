package com.mojang.authlib.yggdrasil;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;

import com.mojang.authlib.Environment;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.minecraft.HttpMinecraftSessionService;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

/**
 * Replaces authlib's Mojang session service.
 *
 * Vanilla's version loads Mojang's session-signing public key from a classpath resource in
 * its constructor and throws java.lang.Error - "Missing/invalid yggdrasil public key!" - if
 * it is not there. TeaVM does not implement Class.getResourceAsStream, so it never is, and
 * the client died inside Minecraft's constructor: this service is built two statements after
 * the pack repository, long before anything is drawn.
 *
 * The right answer is not to find a way to ship the key. This client has no Mojang account
 * and cannot get one - EaglercraftX plays offline or against Eagler-protocol servers, and the
 * User this build constructs carries an empty access token and an offline-mode UUID. A
 * session service that verified Mojang signatures would have nothing to verify.
 *
 * So each method answers the way an unauthenticated client's does, and the two that would
 * otherwise silently pretend are the ones to look at:
 *
 *   - joinServer throws rather than returning quietly. It is only called when a server asks
 *     for online-mode encryption, and succeeding there would tell the client it had
 *     authenticated when it had not - it would then fail further in, with a worse message.
 *   - fillProfileProperties returns the profile untouched, which is exactly what offline
 *     mode does; it does not invent properties.
 *
 * Skins are unaffected: EaglercraftX fetches them through its own skin cache rather than
 * through this service, so getTextures having nothing to offer costs nothing.
 */
public class YggdrasilMinecraftSessionService extends HttpMinecraftSessionService {

	protected YggdrasilMinecraftSessionService(YggdrasilAuthenticationService authenticationService,
			Environment environment) {
		super(authenticationService);
	}

	@Override
	public void joinServer(GameProfile profile, String authenticationToken, String serverId)
			throws AuthenticationException {
		throw new AuthenticationUnavailableException(
				"This client has no Mojang session and cannot join an online-mode server");
	}

	/** Server side; this client never runs it. Null means "not authenticated". */
	@Override
	public GameProfile hasJoinedServer(GameProfile profile, String serverId, InetAddress address)
			throws AuthenticationUnavailableException {
		return null;
	}

	@Override
	public Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> getTextures(
			GameProfile profile, boolean requireSecure) {
		return Collections.emptyMap();
	}

	@Override
	public GameProfile fillProfileProperties(GameProfile profile, boolean requireSecure) {
		return profile;
	}

	protected GameProfile fillGameProfile(GameProfile profile, boolean requireSecure) {
		return profile;
	}

	@Override
	public YggdrasilAuthenticationService getAuthenticationService() {
		return (YggdrasilAuthenticationService) super.getAuthenticationService();
	}
}
