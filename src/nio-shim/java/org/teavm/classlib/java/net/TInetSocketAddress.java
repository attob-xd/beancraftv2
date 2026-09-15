package org.teavm.classlib.java.net;

import java.net.InetAddress;
import java.net.SocketAddress;

/** See TSocketAddress. */
public class TInetSocketAddress extends SocketAddress {

	private final String host;
	private final int port;

	public TInetSocketAddress(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public TInetSocketAddress(InetAddress address, int port) {
		this.host = String.valueOf(address);
		this.port = port;
	}

	public TInetSocketAddress(int port) {
		this("0.0.0.0", port);
	}

	public static TInetSocketAddress createUnresolved(String host, int port) {
		return new TInetSocketAddress(host, port);
	}

	public String getHostName() {
		return host;
	}

	public String getHostString() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public InetAddress getAddress() {
		return null;
	}

	public boolean isUnresolved() {
		return true;
	}

	@Override
	public String toString() {
		return host + ":" + port;
	}
}
