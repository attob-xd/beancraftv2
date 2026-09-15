package net.lax1dude.eaglercraft.compat;

import java.util.Map;
import java.util.WeakHashMap;

import net.lax1dude.eaglercraft.notifications.ServerNotificationManager;
import net.lax1dude.eaglercraft.profile.ServerCapeCache;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController;
import net.lax1dude.eaglercraft.socket.protocol.pkt.GameMessagePacket;
import net.lax1dude.eaglercraft.profile.ServerSkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The per-connection state EaglercraftX added to {@code NetHandlerPlayClient}.
 *
 * <p>In the 1.12.2 fork the skin cache, cape cache and notification manager were fields
 * on the packet handler itself. Doing that in 1.18.2 would mean carrying a patched copy
 * of {@code ClientPacketListener} - 2,277 decompiled lines whose locals lost their
 * generics with the rest of the jar (see the port notes) - just to add four fields. The
 * state is attached from the outside instead, keyed weakly so it dies with the
 * connection it belongs to.
 */
public class EaglerClientState {

	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("EaglerClientState");

	private static final Map<ClientPacketListener, EaglerClientState> STATES = new WeakHashMap<>();

	private final ServerSkinCache skinCache;
	private final ServerCapeCache capeCache;
	private final ServerNotificationManager notifManager;

	private EaglerClientState(ClientPacketListener connection) {
		Minecraft mc = Minecraft.getInstance();
		this.skinCache = new ServerSkinCache(connection, mc.getTextureManager());
		this.capeCache = new ServerCapeCache(connection, mc.getTextureManager());
		this.notifManager = new ServerNotificationManager();
	}

	/** The state for a connection, created on first use. */
	public static EaglerClientState get(ClientPacketListener connection) {
		if (connection == null) {
			return null;
		}
		EaglerClientState state = STATES.get(connection);
		if (state == null) {
			state = new EaglerClientState(connection);
			STATES.put(connection, state);
		}
		return state;
	}

	/** The state for the client's current connection, or null when not connected. */
	public static EaglerClientState current() {
		return get(Minecraft.getInstance().getConnection());
	}

	public ServerSkinCache getSkinCache() {
		return skinCache;
	}

	public ServerCapeCache getCapeCache() {
		return capeCache;
	}

	public ServerNotificationManager getNotifManager() {
		return notifManager;
	}

	// ---- EaglercraftX's plugin-message protocol, client side ----
	// Same controller class as the integrated server uses (see EaglerServerState); the
	// 1.12.2 fork held it as a field on NetHandlerPlayClient.

	private GameProtocolMessageController controller;

	public GameProtocolMessageController getEaglerMessageController() {
		return controller;
	}

	public void setEaglerMessageController(GameProtocolMessageController controller) {
		this.controller = controller;
	}

	public GamePluginMessageProtocol getEaglerMessageProtocol() {
		return controller != null ? controller.protocol : null;
	}

	/** No-op before the handshake has installed a controller. */
	public void sendEaglerMessage(GameMessagePacket packet) {
		if (controller != null) {
			try {
				controller.sendPacket(packet);
			} catch (java.io.IOException ex) {
				// A failed plugin message is not fatal: skins and capes degrade to the
				// vanilla ones, so the connection is left alone.
				LOGGER.error("Failed to send an EaglercraftX plugin message", ex);
			}
		}
	}

	/** Server-info payload the webview screen caches per connection. */
	public byte[] cachedServerInfoData;

	/** Hash of the cached payload, so the server can skip re-sending it. */
	public String cachedServerInfoHash;

	/** Set once this connection has asked the server for its info payload. */
	public boolean hasRequestedServerInfo;

	/** Whether the server opted into EaglercraftX's cookie/auth exchange. */
	public boolean enableCookies;

	/** Whether the server accepts EaglercraftX's HD cape/skin packets. */
	public boolean hdCapesSupported;
	public boolean hdSkinsSupported;

	/** True when this connection is the in-page integrated server, not a remote one. */
	public boolean clientInEaglerSingleplayer;

	public boolean isClientInEaglerSingleplayer() {
		return clientInEaglerSingleplayer;
	}
}
