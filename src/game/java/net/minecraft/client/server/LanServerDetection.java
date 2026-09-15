package net.minecraft.client.server;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.DefaultUncaughtExceptionHandler;
import org.slf4j.Logger;

/**
 * Vanilla's LAN discovery, with the multicast socket taken out.
 *
 * <p>Vanilla finds worlds opened to LAN by joining the multicast group 224.0.2.60 on UDP port
 * 4445 and listening for the beacons {@code LanServerPinger} broadcasts. A page has no UDP at
 * all - no datagrams, no multicast, no way to listen on a port - and TeaVM accordingly has no
 * {@code MulticastSocket}. The constructor's very first statement therefore threw
 * {@code NoSuchMethodError}, and because {@code JoinMultiplayerScreen.init()} starts a detector
 * as it opens, <b>the Multiplayer button crashed the client</b> before the server list ever
 * appeared.
 *
 * <p>The detector is kept as a class because vanilla constructs, interrupts and joins it by
 * name, and it stays a Thread so those calls mean what they say. It simply finds nothing: it
 * ends immediately, which is the truthful result of looking for LAN broadcasts in a browser.
 *
 * <p>{@code LanServerList} is vanilla's, untouched. It is the half that has nothing to do with
 * sockets - EaglercraftX's own LAN worlds arrive through the page's WebRTC relay and are added
 * to this same list - so leaving it intact keeps that path working.
 */
public class LanServerDetection {
	static final AtomicInteger UNIQUE_THREAD_ID = new AtomicInteger(0);
	static final Logger LOGGER = LogUtils.getLogger();

	public static class LanServerDetector extends Thread {

		/**
		 * Declared to throw IOException because vanilla's does and callers catch it; nothing
		 * here can fail.
		 */
		public LanServerDetector(LanServerDetection.LanServerList var1) throws IOException {
			super("LanServerDetector #" + LanServerDetection.UNIQUE_THREAD_ID.incrementAndGet());
			this.setDaemon(true);
			this.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LanServerDetection.LOGGER));
		}

		/**
		 * Ends at once. Vanilla loops on socket.receive() until interrupted; there is no socket
		 * to receive from, and spinning would only burn the one thread the page has.
		 */
		@Override
		public void run() {
			LanServerDetection.LOGGER.debug(
					"LAN discovery is not available in a browser; no multicast listener started");
		}
	}

	public static class LanServerList {
		private final List<LanServer> servers = Lists.newArrayList();
		private boolean isDirty;

		public synchronized boolean isDirty() {
			return this.isDirty;
		}

		public synchronized void markClean() {
			this.isDirty = false;
		}

		public synchronized List<LanServer> getServers() {
			return Collections.unmodifiableList(this.servers);
		}

		public synchronized void addServer(String var1, InetAddress var2) {
			String var3 = LanServerPinger.parseMotd(var1);
			String var4 = LanServerPinger.parseAddress(var1);
			if (var4 != null) {
				var4 = var2.getHostAddress() + ":" + var4;
				boolean var5 = false;

				for (LanServer var7 : this.servers) {
					if (var7.getAddress().equals(var4)) {
						var7.updatePingTime();
						var5 = true;
						break;
					}
				}

				if (!var5) {
					this.servers.add(new LanServer(var3, var4));
					this.isDirty = true;
				}
			}
		}
	}
}
