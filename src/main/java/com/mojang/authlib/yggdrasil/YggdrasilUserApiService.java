package com.mojang.authlib.yggdrasil;

import java.net.Proxy;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.mojang.authlib.Environment;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;

/**
 * Replaces authlib's user API service, which asks Mojang over HTTP - in its constructor -
 * for the account's privileges and block list.
 *
 * Two reasons that cannot work here. There is no account: this build's User carries an empty
 * access token and an offline-mode UUID. And there is no HTTP to ask with: authlib reaches
 * Mojang through java.net.URL.openConnection(Proxy), which TeaVM does not implement, so the
 * call raises NoSuchMethodError - an Error, which slips past Minecraft's catch and takes the
 * client down instead of degrading.
 *
 * The constructor therefore throws AuthenticationException, which is what it is already
 * declared to throw and what Minecraft.createUserApiService is written to handle: it logs
 * that the service is unavailable and uses UserApiService.OFFLINE instead. That is the same
 * state a real client reaches when Mojang is unreachable, so nothing downstream is surprised
 * - chat telemetry is off and no player is on a block list, which is correct for a client
 * that has no Mojang identity to check them against.
 *
 * The interface methods below are unreachable, because no instance is ever constructed. They
 * exist to implement UserApiService and answer the way OFFLINE does.
 */
public class YggdrasilUserApiService implements UserApiService {

	public YggdrasilUserApiService(String accessToken, Proxy proxy, Environment environment)
			throws AuthenticationException {
		throw new AuthenticationException(
				"This client has no Mojang account and no HTTP stack to reach one with;"
						+ " running with UserApiService.OFFLINE");
	}

	@Override
	public UserProperties properties() {
		return UserApiService.OFFLINE_PROPERTIES;
	}

	@Override
	public boolean isBlockedPlayer(UUID playerId) {
		return false;
	}

	@Override
	public void refreshBlockList() {
	}

	@Override
	public TelemetrySession newTelemetrySession(Executor executor) {
		return TelemetrySession.DISABLED;
	}
}
