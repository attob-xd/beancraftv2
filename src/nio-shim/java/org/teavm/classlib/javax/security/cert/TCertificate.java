package org.teavm.classlib.javax.security.cert;

/**
 * The deprecated javax.security.cert hierarchy, which predates java.security.cert and still
 * exists because netty's SSL code references it. Distinct from
 * java.security.cert.Certificate - both are needed, and adding only the java.* one still
 * left the client dying at boot on this.
 *
 * See org.teavm.classlib.java.security.cert.TCertificate: the browser performs the TLS
 * handshake itself, so nothing here ever holds a real certificate.
 */
public abstract class TCertificate {

	protected TCertificate() {
	}

	public abstract byte[] getEncoded();

	public abstract void verify(java.security.PublicKey key);

	public abstract String toString();

	public abstract java.security.PublicKey getPublicKey();
}
