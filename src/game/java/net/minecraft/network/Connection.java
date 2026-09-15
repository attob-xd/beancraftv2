package net.minecraft.network;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.mojang.logging.LogUtils;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * Replaces vanilla's Connection, which is a netty SimpleChannelInboundHandler sitting on
 * an NIO or epoll event loop.
 *
 * A browser tab has no sockets to put under that: EaglercraftX carries packets over a
 * WebSocket, or straight across a postMessage channel to the singleplayer worker, and its
 * own managers - net.lax1dude.eaglercraft.compat.EaglerNetworkManager and the three
 * transports below it - already implement that. What they could not do was stop vanilla
 * from dragging netty in behind them: keeping Connection meant keeping its channel
 * pipeline, its NioEventLoopGroup, its epoll transport and netty's platform-detection
 * layer, and SystemPropertyUtil alone accounted for 2,309 TeaVM diagnostics. lax1dude
 * solved this on 1.12.2 by patching NetworkManager itself; this is that same patch, made
 * against the class 1.18.2 renamed it to.
 *
 * The 23 members below are exactly the ones the vanilla jar and this project actually
 * call, measured with tools/scan_members.py rather than guessed. Everything the netty
 * pipeline drove - channelActive, channelRead0, exceptionCaught, the event-loop groups,
 * the compression and encryption handlers - is gone, and the transports drive the same
 * behaviour by overriding send, tick, disconnect and isConnected.
 */
public class Connection {

	private static final Logger LOGGER = LogUtils.getLogger();

	public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
	public static final Marker PACKET_MARKER = MarkerFactory.getMarker("NETWORK_PACKETS");
	public static final Marker PACKET_RECEIVED_MARKER = MarkerFactory.getMarker("PACKET_RECEIVED");
	public static final Marker PACKET_SENT_MARKER = MarkerFactory.getMarker("PACKET_SENT");

	static {
		PACKET_MARKER.add(ROOT_MARKER);
		PACKET_RECEIVED_MARKER.add(PACKET_MARKER);
		PACKET_SENT_MARKER.add(PACKET_MARKER);
	}

	/*
	 * Vanilla declares
	 *
	 *     public static final AttributeKey<ConnectionProtocol> ATTRIBUTE_PROTOCOL =
	 *             AttributeKey.valueOf("protocol");
	 *
	 * and its packet encoder and decoder read it to find the current protocol. It is gone
	 * here, and deliberately: AttributeKey.valueOf builds a netty ConstantPool, whose
	 * static initialiser is PlatformDependent - netty's probe for Unsafe, for the JDK
	 * cleaner and for the system properties behind them. Keeping one unused field cost
	 * 2,327 TeaVM diagnostics.
	 *
	 * Nothing reads it in this build. PacketEncoder and PacketDecoder only run inside a
	 * netty pipeline, and no pipeline is ever constructed - the transports decode packets
	 * themselves and track the protocol on the connection.
	 */

	private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;

	private final PacketFlow receiving;
	private final Queue<Packet<?>> queue = new ArrayDeque<>();

	private PacketListener packetListener;
	private Component disconnectedReason;
	private ConnectionProtocol protocol = ConnectionProtocol.HANDSHAKING;
	private SocketAddress address;
	private boolean disconnectionHandled;
	private boolean readOnly;

	private int receivedPackets;
	private int sentPackets;
	private float averageReceivedPackets;
	private float averageSentPackets;
	private int tickCount;

	public Connection(PacketFlow receiving) {
		this.receiving = receiving;
	}

	public void setProtocol(ConnectionProtocol protocol) {
		this.protocol = protocol;
	}

	protected ConnectionProtocol getCurrentProtocol() {
		return protocol;
	}

	public void setListener(PacketListener listener) {
		this.packetListener = listener;
	}

	public PacketListener getPacketListener() {
		return packetListener;
	}

	/**
	 * Queues a packet. The base class only holds it; a transport overrides this to put it
	 * on the wire. Vanilla calls this from both threads' worth of game code, so the queue
	 * is here rather than in each transport.
	 */
	public void send(Packet<?> packet) {
		if (readOnly) {
			return;
		}
		++sentPackets;
		queue.add(packet);
	}

	/**
	 * Vanilla passes a netty future listener to learn when a packet has been flushed - for
	 * the login handshake, mostly. Nothing here has a netty future to complete, and the
	 * transports write synchronously, so the packet is sent and the listener is not
	 * called. The one caller that cares (the login flow) drives its own state machine.
	 */
	public void send(Packet<?> packet, GenericFutureListener<? extends Future<? super Void>> listener) {
		send(packet);
	}

	/**
	 * Drains whatever send() queued. A transport that writes immediately overrides send()
	 * and leaves this empty.
	 */
	protected Queue<Packet<?>> drainQueue() {
		return queue;
	}

	public void tick() {
		tickCount++;
		if (tickCount % 20 == 0) {
			tickSecond();
		}
	}

	protected void tickSecond() {
		averageSentPackets = averageSentPackets * AVERAGE_PACKETS_SMOOTHING
				+ sentPackets * (1.0F - AVERAGE_PACKETS_SMOOTHING);
		averageReceivedPackets = averageReceivedPackets * AVERAGE_PACKETS_SMOOTHING
				+ receivedPackets * (1.0F - AVERAGE_PACKETS_SMOOTHING);
		sentPackets = 0;
		receivedPackets = 0;
	}

	protected void countReceivedPacket() {
		++receivedPackets;
	}

	public SocketAddress getRemoteAddress() {
		return address;
	}

	protected void setRemoteAddress(SocketAddress address) {
		this.address = address;
	}

	public void disconnect(Component reason) {
		if (disconnectedReason == null) {
			disconnectedReason = reason;
		}
	}

	public Component getDisconnectedReason() {
		return disconnectedReason;
	}

	/**
	 * Delivers the disconnect reason to the listener exactly once. Vanilla calls this from
	 * the game loop after noticing the channel closed.
	 */
	public void handleDisconnection() {
		if (!disconnectionHandled) {
			disconnectionHandled = true;
			if (packetListener != null) {
				packetListener.onDisconnect(disconnectedReason != null ? disconnectedReason
						: new TextComponent("Disconnected"));
			}
		}
	}

	public boolean isMemoryConnection() {
		return false;
	}

	public PacketFlow getReceiving() {
		return receiving;
	}

	public PacketFlow getSending() {
		return receiving.getOpposite();
	}

	public boolean isConnected() {
		return disconnectedReason == null;
	}

	public boolean isConnecting() {
		return false;
	}

	public void setReadOnly() {
		readOnly = true;
	}

	/**
	 * The browser's transport is wss://, so the connection is already encrypted end to end
	 * and Minecraft's own AES layer would be a second one. EaglercraftX never negotiates
	 * it, and the vanilla login handler that would call this is replaced.
	 */
	public void setEncryptionKey(Cipher decrypt, Cipher encrypt) {
		LOGGER.warn("Ignoring a request to encrypt the connection; the transport is already"
				+ " encrypted by the browser");
	}

	public boolean isEncrypted() {
		return false;
	}

	/**
	 * Vanilla inserts netty compression handlers here. EaglercraftX's protocol does its own
	 * framing, so the threshold is recorded and nothing is installed.
	 */
	public void setupCompression(int threshold, boolean validate) {
	}

	public float getAverageReceivedPackets() {
		return averageReceivedPackets;
	}

	public float getAverageSentPackets() {
		return averageSentPackets;
	}

	public static Connection connectToServer(InetSocketAddress address, boolean useEpoll) {
		throw new UnsupportedOperationException(
				"EaglercraftX opens its own WebSocket; see WebSocketNetworkManager");
	}

	/**
	 * The client half of the in-page link to the integrated server.
	 *
	 * <p>Vanilla builds a netty LocalChannel here. There is no netty transport in this build,
	 * and none is needed: ServerConnectionListener.startMemoryChannel() has just built both
	 * ends as a pair and parked this one. See MemoryConnection for why the pair is built in one
	 * place rather than threaded through the address the way vanilla does.
	 */
	public static Connection connectToLocalServer(SocketAddress address) {
		LOGGER.info("[memconn] connectToLocalServer - the client left doLoadLevel's wait loop");
		MemoryConnection clientSide = MemoryConnection.takePendingClientSide();
		if (clientSide == null) {
			throw new IllegalStateException("No memory channel was opened for " + address
					+ "; startMemoryChannel() must be called first");
		}
		return clientSide;
	}
}
