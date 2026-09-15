package net.minecraft.server.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;

/**
 * Replaces the server's netty listener, which binds a ServerSocketChannel on an NIO or
 * epoll event loop and installs the vanilla packet pipeline on every accepted connection.
 *
 * The integrated server here has nothing to bind: players reach it either as the local
 * player - through the postMessage channel the singleplayer worker already owns - or, on
 * an EaglercraftX LAN world, through the page's WebRTC relay, which
 * net.lax1dude.eaglercraft.sp.server.socket drives instead. MinecraftServer constructs
 * this class unconditionally, so it stays, holding the connection list vanilla iterates
 * and refusing the two calls that would need a socket.
 */
public class ServerConnectionListener {

	final MinecraftServer server;
	public volatile boolean running;
	final List<Connection> connections = Collections.synchronizedList(new ArrayList<>());

	public ServerConnectionListener(MinecraftServer server) {
		this.server = server;
		this.running = true;
	}

	public void startTcpServerListener(InetAddress address, int port) throws IOException {
		throw new IOException("EaglercraftX's integrated server has no TCP listener;"
				+ " players connect through the page's relay");
	}

	/**
	 * Opens the in-page link the local player joins through.
	 *
	 * <p>Vanilla binds a netty LocalChannel and installs the server pipeline on it. Here both
	 * ends are plain objects - see {@link net.minecraft.network.MemoryConnection} - so this
	 * builds the pair, keeps the server half in the same connection list vanilla iterates and
	 * ticks, and gives it the handshake listener that the pipeline would have installed. The
	 * client half is collected moments later by Connection.connectToLocalServer.
	 *
	 * <p>The returned address is a label: vanilla's is a netty LocalAddress that is likewise
	 * never resolved, and it ends up in the server log and in the client's intention packet.
	 */
	private static final org.slf4j.Logger MEMCONN_LOG = LogUtils.getLogger();

	public SocketAddress startMemoryChannel() {
		MEMCONN_LOG.info("[memconn] startMemoryChannel - the server is opening its half");
		net.minecraft.network.MemoryConnection serverSide =
				net.minecraft.network.MemoryConnection.openPair();
		serverSide.setListener(new ServerHandshakePacketListenerImpl(server, serverSide));
		connections.add(serverSide);
		return net.minecraft.network.MemoryConnection.LOCAL_ADDRESS;
	}

	public void stop() {
		running = false;
	}

	/**
	 * Vanilla ticks each connection here and drops the ones that have gone away. The
	 * transports do their own receive pumping, so this only reaps.
	 */
	public void tick() {
		synchronized (connections) {
			connections.removeIf(connection -> {
				if (connection.isConnected()) {
					connection.tick();
					return false;
				}
				connection.handleDisconnection();
				return true;
			});
		}
	}

	public MinecraftServer getServer() {
		return server;
	}

	public List<Connection> getConnections() {
		return connections;
	}
}
