package org.teavm.classlib.java.security.cert;

/**
 * Missing from TeaVM's class library, and this one is not academic: netty names it while
 * initialising, and the client died on the first boot that got that far with
 * NoClassDefFoundError. Certificates belong to the TLS handshake, which the browser
 * performs itself before EaglercraftX sees a byte - see net.minecraft.util.Crypt.
 */
public abstract class TCertificate {

	private final String type;

	protected TCertificate(String type) {
		this.type = type;
	}

	public final String getType() {
		return type;
	}

	public abstract byte[] getEncoded();

	public abstract String toString();
}
