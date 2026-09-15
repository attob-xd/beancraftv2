package org.teavm.classlib.java.net;

import java.net.SocketAddress;

/**
 * Missing from TeaVM's class library. Minecraft threads a Proxy through everything that
 * might make an HTTP request, and hands NO_PROXY when the launcher did not configure one.
 * A page cannot choose its own proxy - the browser decides - so NO_PROXY is the only value
 * this ever holds.
 */
public class TProxy {

	public enum Type {
		DIRECT, HTTP, SOCKS
	}

	public static final TProxy NO_PROXY = new TProxy(Type.DIRECT, null);

	private final Type type;
	private final SocketAddress address;

	public TProxy(Type type, SocketAddress address) {
		this.type = type;
		this.address = address;
	}

	public Type type() {
		return type;
	}

	public SocketAddress address() {
		return address;
	}

	@Override
	public String toString() {
		return type == Type.DIRECT ? "DIRECT" : type + " @ " + address;
	}
}
