package net.minecraft.client.multiplayer;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Replaces the multiplayer server-list pinger.
 *
 * Vanilla opens a real TCP connection per entry - a netty Bootstrap on an NIO or epoll
 * event loop - sends a status request, and falls back to the pre-1.7 "legacy ping" on a
 * second socket if that fails. Keeping it was what held netty's whole transport stack in
 * the build after Connection had been taken off it: NioEventLoop, NioSocketChannel,
 * PlatformDependent0's Unsafe probing and MacAddressUtil, roughly 160 diagnostics for code
 * that cannot open a socket from a page.
 *
 * EaglercraftX pings over its own protocol instead - the server list talks to the relay
 * and to each server's WebSocket, and net.lax1dude.eaglercraft.socket drives it - so the
 * vanilla pinger has nothing to do. Entries it is asked about are marked unreachable,
 * which is what vanilla shows when a ping fails, and the EaglercraftX server list fills in
 * the real status itself.
 */
public class ServerStatusPinger {

	static final Logger LOGGER = LogUtils.getLogger();

	private static final Component CANT_CONNECT_MESSAGE =
			new TranslatableComponent("multiplayer.status.cannot_connect");

	private final List<Connection> connections = new ArrayList<>();

	public void pingServer(ServerData server, Runnable onDone) throws UnknownHostException {
		onPingFailed(CANT_CONNECT_MESSAGE, server);
		onDone.run();
	}

	void onPingFailed(Component reason, ServerData server) {
		server.ping = -1L;
		server.motd = reason;
		server.status = null;
		server.playerList = null;
	}

	public void tick() {
	}

	public void removeAll() {
		connections.clear();
	}

	static Component formatPlayerCount(int online, int max) {
		return new TranslatableComponent("multiplayer.status.player_count", online, max);
	}
}
