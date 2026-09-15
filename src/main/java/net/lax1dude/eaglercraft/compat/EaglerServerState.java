package net.lax1dude.eaglercraft.compat;

import java.util.Map;
import java.util.WeakHashMap;

import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.socket.protocol.pkt.GameMessagePacket;
import net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The EaglercraftX plugin-message state that used to live on {@code NetHandlerPlayServer}.
 *
 * <p>The integrated server speaks EaglercraftX's own protocol (skins, capes, notifications)
 * over Minecraft's plugin-message channel, and the 1.12.2 fork held the controller as a
 * field on the server play handler. 1.18.2's {@code ServerGamePacketListenerImpl} comes
 * from the jar, so the state is attached from outside - the same arrangement as
 * {@link EaglerClientState} on the client side, keyed weakly so it dies with the
 * connection.
 */
public class EaglerServerState {

	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("EaglerServerState");

	private static final Map<ServerGamePacketListenerImpl, EaglerServerState> STATES = new WeakHashMap<>();

	private GameProtocolMessageController controller;

	/** The state for a connection, created on first use. */
	public static EaglerServerState get(ServerGamePacketListenerImpl connection) {
		if (connection == null) {
			return null;
		}
		EaglerServerState state = STATES.get(connection);
		if (state == null) {
			state = new EaglerServerState();
			STATES.put(connection, state);
		}
		return state;
	}

	public GameProtocolMessageController getEaglerMessageController() {
		return controller;
	}

	public void setEaglerMessageController(GameProtocolMessageController controller) {
		this.controller = controller;
	}

	public GamePluginMessageProtocol getEaglerMessageProtocol() {
		return controller != null ? controller.protocol : null;
	}

	/** No-op when the client is not an EaglercraftX client - it has no controller. */
	public void sendEaglerMessage(GameMessagePacket packet) {
		if (controller != null) {
			try {
				controller.sendPacket(packet);
			} catch (java.io.IOException ex) {
				// Not fatal - the client simply keeps its vanilla skin/cape.
				LOGGER.error("Failed to send an EaglercraftX plugin message", ex);
			}
		}
	}

	// ---- per-player state EaglercraftX kept on ServerPlayer ----

	private static final Map<net.minecraft.server.level.ServerPlayer, java.util.UUID> BRAND_UUIDS =
			new WeakHashMap<>();

	/** The client-brand UUID a player reported during the EaglercraftX handshake. */
	public static java.util.UUID getClientBrandUUID(net.minecraft.server.level.ServerPlayer player) {
		return BRAND_UUIDS.get(player);
	}

	public static void setClientBrandUUID(net.minecraft.server.level.ServerPlayer player,
			java.util.UUID uuid) {
		BRAND_UUIDS.put(player, uuid);
	}

	/** The integrated-server controller this connection belongs to, when there is one. */
	public Object serverController;
}
