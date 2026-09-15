package org.teavm.classlib.java.net;

/**
 * Missing from TeaVM's class library, and the most-referenced of them: 83 places name it,
 * because every network API in Minecraft is typed on it.
 *
 * A page has no sockets, so this carries no address of its own. It exists so those
 * references resolve - EaglercraftX's own managers return a subclass whose toString() is
 * the WebSocket URI (see EaglerNetworkManager.getRemoteAddress), which is what a server
 * operator would recognise anyway.
 */
public abstract class TSocketAddress {

	protected TSocketAddress() {
	}
}
