package org.teavm.classlib.java.net;

/**
 * Missing from TeaVM's class library. A page cannot resolve a host name - there is no
 * resolver behind a browser tab, which is also why the SRV lookup in ServerRedirectHandler
 * had to go - so this holds the name it was given and nothing more.
 */
public class TInetAddress {

	private final String host;

	protected TInetAddress(String host) {
		this.host = host;
	}

	public static TInetAddress getByName(String host) {
		return new TInetAddress(host);
	}

	public static TInetAddress getLocalHost() {
		return new TInetAddress("localhost");
	}

	public String getHostName() {
		return host;
	}

	public String getHostAddress() {
		return host;
	}

	public String getCanonicalHostName() {
		return host;
	}

	public boolean isAnyLocalAddress() {
		return false;
	}

	public boolean isLoopbackAddress() {
		return "localhost".equals(host) || "127.0.0.1".equals(host);
	}

	public byte[] getAddress() {
		return new byte[4];
	}

	@Override
	public String toString() {
		return host;
	}
}
