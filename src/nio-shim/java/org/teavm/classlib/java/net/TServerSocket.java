package org.teavm.classlib.java.net;

import java.io.IOException;

/**
 * Missing from TeaVM's class library, and unusable here: a page cannot open a TCP or UDP
 * socket. It exists because vanilla's dead server code names the type in class metadata,
 * which TeaVM evaluates at load time - so the class has to resolve even though nothing can
 * construct it. Constructing one says so.
 */
public class TServerSocket {

	public TServerSocket() throws IOException {
		throw new IOException("A browser tab cannot open sockets; EaglercraftX connects"
				+ " over WebSocket");
	}

	public void close() {
	}
}
