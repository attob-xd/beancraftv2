package net.minecraft.client.multiplayer.resolver;

import java.util.Optional;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Replaces vanilla's SRV-record redirect handler.
 *
 * Vanilla resolves _minecraft._tcp SRV records by opening a JNDI InitialDirContext against
 * the "dns:" provider. That one call is what put the whole JDK into this build: JNDI's
 * NamingManager instantiates its provider by reflection, TeaVM answers a reflective
 * newInstance() with "any class may be constructed", and from there every lambda in the
 * JDK became a candidate for every BiFunction call site in the program - Swing, java2d and
 * the image codecs included.
 *
 * The browser could not have used it anyway. EaglercraftX does not open TCP sockets; it
 * connects over WebSocket to an address the user types or picks from the server list, and
 * a page has no DNS resolver to ask for SRV records. Returning the address unchanged is
 * what the vanilla handler does when no record is found, so callers see nothing new.
 */
public interface ServerRedirectHandler {

	Logger LOGGER = LogUtils.getLogger();

	ServerRedirectHandler EMPTY = address -> Optional.empty();

	Optional<ServerAddress> lookupRedirect(ServerAddress address);

	static ServerRedirectHandler createDnsSrvRedirectHandler() {
		return EMPTY;
	}
}
