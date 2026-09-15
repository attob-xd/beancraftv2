package net.minecraft.network;

import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import com.mojang.logging.LogUtils;

/**
 * The client-to-integrated-server link, as a pair of objects rather than a netty pipeline.
 *
 * <p>Vanilla's {@code Minecraft.doLoadLevel} starts an in-process {@code IntegratedServer} and
 * then joins it over a netty {@code LocalChannel}: {@code startMemoryChannel()} on the server
 * side, {@code Connection.connectToLocalServer()} on the client side. Netty's transport is cut
 * from this build - there are no channels, no event loops and no sockets in a browser - so both
 * calls used to throw, and Singleplayer died at the last step with the world already generated
 * and waiting.
 *
 * <p>A local channel does not actually need any of netty. Both ends live in the same page, on
 * the same thread, so a packet can be handed straight from one side to the other. Two of these
 * are created as a pair, each holding the other; {@code send} appends to the peer's inbound
 * queue and {@code tick} drains that queue into the peer's listener.
 *
 * <p><b>Queueing rather than delivering directly is the important part.</b> Login is a
 * conversation - the client's hello provokes the server's game profile, which provokes the
 * client's join - and delivering inline would run each reply inside the handler of the message
 * that caused it, nesting the two sides' state machines arbitrarily deep and letting a listener
 * be replaced while it was still on the stack. Vanilla gets the same separation from netty's
 * event loop. Draining on tick reproduces it, and it is also what vanilla's own
 * {@code Connection.tick} contract already expects.
 *
 * <p>Nothing is serialised. Vanilla does not serialise over a local channel either, which is
 * why {@code isMemoryConnection()} exists and why the server skips compression and encryption
 * for it. The packets are the same objects on both sides, so this is both faster and less code
 * than a byte pipe would be.
 */
public class MemoryConnection extends Connection {

	/**
	 * The address both ends report. Vanilla puts the real {@code LocalAddress} here, and the
	 * value reaches the server's logs and the client's intention packet; it is never resolved.
	 */
	public static final SocketAddress LOCAL_ADDRESS = new SocketAddress() {
		private static final long serialVersionUID = 1L;

		@Override
		public String toString() {
			return "local";
		}
	};

	/**
	 * The client half, parked between {@code startMemoryChannel()} building the pair and
	 * {@code Connection.connectToLocalServer()} asking for it.
	 *
	 * <p>Vanilla threads the two calls together through a netty address; here the pair has to
	 * be built in one place, because each end needs a reference to the other. Those two calls
	 * are adjacent statements in {@code doLoadLevel} on one thread, so the handoff is only ever
	 * held for the length of that pair of statements.
	 */
	private static MemoryConnection pendingClientSide;

	private final Queue<Packet<?>> inbound = new ArrayDeque<>();
	private MemoryConnection peer;
	private boolean open = true;

	private MemoryConnection(PacketFlow receiving) {
		super(receiving);
	}

	/**
	 * Builds both ends and parks the client half for {@link Connection#connectToLocalServer}.
	 *
	 * @return the server half, for the caller to register and give a listener
	 */
	public static MemoryConnection openPair() {
		trace("openPair - a memory channel is being created");
		MemoryConnection serverSide = new MemoryConnection(PacketFlow.SERVERBOUND);
		MemoryConnection clientSide = new MemoryConnection(PacketFlow.CLIENTBOUND);
		serverSide.peer = clientSide;
		clientSide.peer = serverSide;
		pendingClientSide = clientSide;
		return serverSide;
	}

	/** The client half built by the most recent {@link #openPair()}, or null if there is none. */
	public static MemoryConnection takePendingClientSide() {
		trace("takePendingClientSide - the client is claiming its half"
				+ (pendingClientSide == null ? " (NONE PENDING)" : ""));
		MemoryConnection out = pendingClientSide;
		pendingClientSide = null;
		return out;
	}

	@Override
	public void send(Packet<?> packet) {
		if (!open || peer == null) {
			trace("send DROPPED " + name(packet) + (open ? " (no peer)" : " (closed)"));
			return;
		}
		trace((getReceiving() == PacketFlow.CLIENTBOUND ? "client" : "server")
				+ " -> send " + name(packet));
		peer.inbound.add(packet);
	}

	@Override
	public void send(Packet<?> packet,
			io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>> listener) {
		send(packet);
	}

	/**
	 * Delivers whatever the peer sent since the last tick.
	 *
	 * <p>The queue is drained by count rather than until empty: a handler may well send a reply
	 * that the peer answers within the same tick, and draining until empty could let the two
	 * sides volley indefinitely without the game ever advancing. Taking a snapshot of the size
	 * bounds the work to what had already arrived.
	 */
	@Override
	public void tick() {
		super.tick();
		int pending = inbound.size();
		while (pending-- > 0 && open) {
			Packet<?> packet = inbound.poll();
			if (packet == null) {
				break;
			}
			PacketListener listener = getPacketListener();
			if (listener == null) {
				// The other side is ahead of us; put it back and wait for a listener.
				trace("deliver STALLED " + name(packet) + " - no listener on this end yet");
				inbound.add(packet);
				break;
			}
			trace((getReceiving() == PacketFlow.CLIENTBOUND ? "client" : "server")
					+ " <- deliver " + name(packet));
			handle(packet, listener);
		}
	}

	/**
	 * {@code Packet<T extends PacketListener>.handle(T)} cannot be called on a
	 * {@code Packet<?>} with a plain listener - the wildcard hides the listener type the packet
	 * expects. The protocol state already guarantees they match; this is the same cast
	 * EaglerNetworkManager makes for the same reason.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void handle(Packet<?> packet, PacketListener listener) {
		((Packet)packet).handle(listener);
	}

	/**
	 * Temporary handshake tracing.
	 *
	 * <p>The singleplayer join is the one step of world loading with no logging of its own: the
	 * server reports that it is ready and ticking, the client shows a loading screen, and if no
	 * packet ever crosses between them nothing says so. Capped, because a joined world sends
	 * thousands of packets a second and the browser console keeps only the most recent few
	 * hundred lines - an uncapped trace erases the very beginning it exists to show.
	 */
	private static final org.slf4j.Logger TRACE_LOG = LogUtils.getLogger();

	private static int traceCount;

	private static void trace(String message) {
		if (traceCount < 60) {
			++traceCount;
			TRACE_LOG.info("[memconn {}] {}", traceCount, message);
		}
	}

	private static String name(Packet<?> packet) {
		if (packet == null) {
			return "null";
		}
		String n = packet.getClass().getName();
		int dot = n.lastIndexOf('.');
		return dot < 0 ? n : n.substring(dot + 1);
	}

	@Override
	public boolean isMemoryConnection() {
		return true;
	}

	@Override
	public boolean isConnected() {
		return open;
	}

	@Override
	public boolean isConnecting() {
		return false;
	}

	@Override
	public SocketAddress getRemoteAddress() {
		return LOCAL_ADDRESS;
	}

	@Override
	public void disconnect(Component reason) {
		if (!open) {
			return;
		}
		open = false;
		super.disconnect(reason != null ? reason : new TextComponent("Disconnected"));
		// Close the far end too: a local channel has no socket whose death the peer could
		// notice, so leaving it open would strand the other side waiting for packets that
		// can no longer arrive.
		if (peer != null && peer.open) {
			peer.disconnect(reason);
		}
	}
}
