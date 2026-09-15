package net.lax1dude.eaglercraft.compat;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.Unpooled;

import net.lax1dude.eaglercraft.internal.EnumEaglerConnectionState;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * EaglercraftX's transport base, ported from the 1.12.2 fork's {@code NetworkManager}.
 *
 * <p>The browser has no sockets, so EaglercraftX replaced Minecraft's netty-backed
 * connection with its own abstract base and drove packets over a WebSocket. In the
 * 1.12.2 fork it could simply overwrite {@code net.minecraft.network.NetworkManager},
 * because Minecraft was compiled from source alongside it. Here vanilla comes from the
 * 1.18.2 jar and hands its listeners a {@link Connection}, which cannot be replaced -
 * so the same API extends Connection instead of standing in for it, and vanilla keeps
 * accepting these managers wherever it expects a connection.
 */
public abstract class EaglerNetworkManager extends Connection {

	public static final Logger logger = LogManager.getLogger("NetworkManager");

	protected final String address;
	protected PacketListener nethandler = null;
	protected ConnectionProtocol packetState = ConnectionProtocol.HANDSHAKING;
	protected final FriendlyByteBuf temporaryBuffer;
	protected int debugPacketCounter = 0;
	protected String pluginBrand = null;
	protected String pluginVersion = null;
	protected boolean clientDisconnected = false;

	/**
	 * The EaglercraftX plugin-message protocol agreed during the handshake.
	 *
	 * <p>The 1.12.2 fork smuggled this through the login-success packet, whose payload
	 * it had replaced. 1.18.2's ClientboundGameProfilePacket carries a real GameProfile,
	 * so the negotiated version is kept on the connection instead.
	 */
	protected int eaglerProtocolVersion = -1;

	public int getEaglerProtocolVersion() {
		return eaglerProtocolVersion;
	}

	public void setEaglerProtocolVersion(int version) {
		this.eaglerProtocolVersion = version;
	}

	public EaglerNetworkManager(String address) {
		super(PacketFlow.CLIENTBOUND);
		this.address = address;
		this.temporaryBuffer = new FriendlyByteBuf(Unpooled.buffer(0x1FFFF));
	}

	public void setPluginInfo(String pluginBrand, String pluginVersion) {
		this.pluginBrand = pluginBrand;
		this.pluginVersion = pluginVersion;
	}

	public String getPluginBrand() {
		return pluginBrand;
	}

	public String getPluginVersion() {
		return pluginVersion;
	}

	public abstract void connect();

	public abstract EnumEaglerConnectionState getConnectStatus();

	public String getAddress() {
		return address;
	}

	public abstract void closeChannel(Component reason);

	/**
	 * Overridden because {@link Connection#setProtocol} writes a different field than the one
	 * these transports actually dispatch on.
	 *
	 * <p>Vanilla keeps the protocol in {@code Connection.protocol}; EaglercraftX's transports
	 * read {@code packetState} in their own send/receive loops. Both setters existed, and the
	 * two halves of the codebase picked different ones: the multiplayer path calls
	 * {@code setConnectionState}, while every singleplayer transition - client LOGIN, server
	 * LOGIN, and the move to PLAY on both ends - calls {@code setProtocol}. So the singleplayer
	 * connection never left HANDSHAKING as far as the packet codec was concerned, and the first
	 * thing sent after the world loaded failed with "Incorrect packet for state:
	 * ServerboundHelloPacket" before a byte went out.
	 *
	 * <p>Keeping them in lockstep here, rather than correcting each call site, is what stops it
	 * coming back: there is now no way to set one without the other.
	 */
	@Override
	public void setProtocol(ConnectionProtocol state) {
		packetState = state;
		super.setProtocol(state);
	}

	public void setConnectionState(ConnectionProtocol state) {
		setProtocol(state);
	}

	public abstract void processReceivedPackets() throws IOException;

	public abstract void sendPacket(Packet<?> pkt);

	@Override
	public void setListener(PacketListener nethandler) {
		this.nethandler = nethandler;
	}

	public boolean isLocalChannel() {
		return false;
	}

	public boolean isChannelOpen() {
		return getConnectStatus() == EnumEaglerConnectionState.CONNECTED;
	}

	public boolean getIsencrypted() {
		return false;
	}

	public abstract boolean checkDisconnected();

	/**
	 * {@code Packet<T extends PacketListener>.handle(T)} cannot be called on a
	 * {@code Packet<?>} with a plain listener - the wildcard hides the listener type the
	 * packet expects. The transport decodes packets by numeric id, so it only ever has
	 * the wildcard; the protocol state already guarantees the listener matches.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected static void handlePacket(Packet<?> pkt, PacketListener listener) {
		if (handleEaglerPluginMessage(pkt, listener)) {
			return;
		}
		// A packet trace at DEBUG. There is one thread in a browser tab, so a handler that does
		// not return takes the whole page with it - no frames, no further logging, and no way
		// to ask the page anything afterwards because script cannot run either. The last line
		// in the console is then the only evidence of which handler it was, and without this
		// there is no such line.
		((Packet) pkt).handle(listener);
	}

	/**
	 * Delivers EaglercraftX's own plugin messages, and says whether it took the packet.
	 *
	 * <p>This is the receive half of the protocol that carries skins, capes, voice signalling
	 * and server notifications, and before this method existed there was no receive half at
	 * all. Both ends could construct a {@code GameProtocolMessageController} and send through
	 * it, but nothing ever called {@code handlePacket} on one, so every reply was handed to
	 * vanilla, which logged "Unknown custom packet identifier" and dropped it. The visible
	 * consequence was that other players never got their skin or cape: the client asked for
	 * one on first sight of a player, the answer arrived, and it went in the bin.
	 *
	 * <p>It sits here because all three transports in this build - the WebSocket client, the
	 * singleplayer client end and the integrated server end - extend this class and dispatch
	 * through this one method, so one hook serves every direction. Vanilla's own listener is
	 * bypassed only for channels {@link EaglerPluginChannels} recognises; anything else,
	 * including an ordinary mod's plugin message, is passed straight through untouched.
	 *
	 * <p>A failure here is logged and swallowed rather than propagated. A malformed skin packet
	 * is not a reason to drop a player's connection, and the caller's contract is that
	 * returning true means "this packet is dealt with".
	 */
	private static boolean handleEaglerPluginMessage(Packet<?> pkt, PacketListener listener) {
		net.minecraft.resources.ResourceLocation id;
		FriendlyByteBuf data;
		if (pkt instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket) {
			net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket p =
					(net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket) pkt;
			id = p.getIdentifier();
			data = p.getData();
		} else if (pkt instanceof net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket) {
			net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket p =
					(net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket) pkt;
			id = p.getIdentifier();
			data = p.getData();
		} else {
			return false;
		}

		String channel = EaglerPluginChannels.toChannel(id);
		if (channel == null) {
			return false;
		}

		net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController controller = null;
		if (listener instanceof net.minecraft.client.multiplayer.ClientPacketListener) {
			EaglerClientState state =
					EaglerClientState.get((net.minecraft.client.multiplayer.ClientPacketListener) listener);
			controller = state == null ? null : state.getEaglerMessageController();
		} else if (listener instanceof net.minecraft.server.network.ServerGamePacketListenerImpl) {
			EaglerServerState state =
					EaglerServerState.get((net.minecraft.server.network.ServerGamePacketListenerImpl) listener);
			controller = state == null ? null : state.getEaglerMessageController();
		}

		if (controller == null) {
			// The handshake has not installed one yet, or this end does not speak the
			// protocol. Still ours, so vanilla must not see it and log it as unknown.
			logger.warn("Dropped an EaglercraftX message on {} - no protocol controller yet", channel);
			return true;
		}

		try {
			if (!controller.handlePacket(channel, data)) {
				logger.warn("Unhandled EaglercraftX message on channel {}", channel);
			}
		} catch (Throwable t) {
			logger.error("Failed to handle an EaglercraftX message on channel {}", channel);
			logger.error(t);
		}
		return true;
	}

	/**
	 * Vanilla holds these managers as a plain {@link Connection} and calls its API, so the
	 * six methods it actually uses are routed to the transport. Connection is a source
	 * class in this port precisely so this is possible - see its header for why.
	 */
	@Override
	public void send(Packet<?> pkt) {
		switchProtocolFor(pkt);
		sendPacket(pkt);
	}

	@Override
	public void send(Packet<?> pkt,
			io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>> listener) {
		switchProtocolFor(pkt);
		sendPacket(pkt);
	}

	/**
	 * Moves to the protocol the outgoing packet belongs to, which is vanilla's behaviour and
	 * was lost when send() was routed to the transport.
	 *
	 * <p>{@code Connection.doSendPacket} compares
	 * {@code ConnectionProtocol.getProtocolForPacket(pkt)} against the current protocol and
	 * calls {@code setProtocol} when they differ - so on vanilla nothing ever has to announce
	 * the LOGIN-to-PLAY transition on the sending side; the first PLAY packet performs it.
	 *
	 * <p>The client half never noticed, because GuiScreenSingleplayerConnecting and
	 * NetHandlerSingleplayerLogin both call setProtocol explicitly. The server half has
	 * nothing that does: ServerLoginPacketListenerImpl hands off to PlayerList.placeNewPlayer
	 * and simply starts sending PLAY packets. So the integrated server stayed in LOGIN for
	 * ever and every packet after the handshake died in sendPacket's getPacketId with
	 * "Incorrect packet for state: ClientboundRotateHeadPacket" - the player was in the world,
	 * the server was ticking it, and not one byte of it could reach the client.
	 */
	private void switchProtocolFor(Packet<?> pkt) {
		ConnectionProtocol want = ConnectionProtocol.getProtocolForPacket(pkt);
		if (want != null && want != packetState) {
			setProtocol(want);
		}
	}

	@Override
	public boolean isConnected() {
		return isChannelOpen();
	}

	@Override
	public boolean isConnecting() {
		return getConnectStatus() == EnumEaglerConnectionState.CONNECTING;
	}

	@Override
	public void disconnect(Component reason) {
		closeChannel(reason);
	}

	@Override
	public void tick() {
		try {
			processReceivedPackets();
		} catch (IOException ex) {
			logger.error("Failed to process received packets", ex);
			closeChannel(net.minecraft.network.chat.Component.nullToEmpty(
					"Failed to process received packets"));
		}
		super.tick();
	}

	@Override
	public boolean isMemoryConnection() {
		return isLocalChannel();
	}

	@Override
	public boolean isEncrypted() {
		return getIsencrypted();
	}

	/**
	 * Vanilla logs and rate-limits by remote address. There is no socket behind this, so
	 * the WebSocket URI stands in - it is what a server operator would recognise anyway.
	 */
	@Override
	public java.net.SocketAddress getRemoteAddress() {
		return new java.net.SocketAddress() {
			@Override
			public String toString() {
				return address;
			}
		};
	}

	protected void doClientDisconnect(Component msg) {
		if (!clientDisconnected) {
			clientDisconnected = true;
			if (nethandler != null) {
				this.nethandler.onDisconnect(msg);
			}
		}
	}
}
