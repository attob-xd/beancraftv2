package org.teavm.classlib.java.net;

import java.net.InetAddress;
import java.net.SocketAddress;

/**
 * Missing from TeaVM's class library. Referenced by the query and RCON server code, which
 * cannot run here - see TSocket. This is a plain data holder so those references resolve.
 */
public class TDatagramPacket {

	private byte[] data;
	private int offset;
	private int length;
	private SocketAddress address;

	public TDatagramPacket(byte[] data, int length) {
		this(data, 0, length);
	}

	public TDatagramPacket(byte[] data, int offset, int length) {
		this.data = data;
		this.offset = offset;
		this.length = length;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public int getOffset() {
		return offset;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public SocketAddress getSocketAddress() {
		return address;
	}

	public void setSocketAddress(SocketAddress address) {
		this.address = address;
	}

	public InetAddress getAddress() {
		return null;
	}

	public int getPort() {
		return 0;
	}
}
