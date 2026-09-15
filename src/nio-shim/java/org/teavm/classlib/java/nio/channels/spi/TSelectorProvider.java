package org.teavm.classlib.java.nio.channels.spi;

/**
 * Missing from TeaVM's class library. netty asks the provider for a Selector when it opens
 * an event loop; nothing opens one here. See java.nio.channels.Selector.
 */
public abstract class TSelectorProvider {

	protected TSelectorProvider() {
	}

	public static TSelectorProvider provider() {
		throw new UnsupportedOperationException(
				"A browser tab has no selectable channels");
	}
}
