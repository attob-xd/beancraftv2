package org.teavm.classlib.java.nio.channels;

/** Missing from TeaVM's class library. There are no channels to connect; see TFileChannel. */
public class TNotYetConnectedException extends IllegalStateException {

	public TNotYetConnectedException() {
	}

	public TNotYetConnectedException(String message) {
		super(message);
	}
}
