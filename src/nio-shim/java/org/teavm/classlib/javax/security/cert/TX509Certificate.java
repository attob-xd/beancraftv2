package org.teavm.classlib.javax.security.cert;

import java.math.BigInteger;
import java.util.Date;

/** See TCertificate. netty names this while initialising its SSL support. */
public abstract class TX509Certificate extends javax.security.cert.Certificate {

	protected TX509Certificate() {
	}

	public static TX509Certificate getInstance(byte[] encoded) {
		throw new UnsupportedOperationException(
				"Certificates are the browser's business; EaglercraftX never parses one");
	}

	public abstract void checkValidity();

	public abstract void checkValidity(Date date);

	public abstract int getVersion();

	public abstract BigInteger getSerialNumber();

	public abstract Date getNotBefore();

	public abstract Date getNotAfter();

	public abstract String getSigAlgName();

	public abstract String getSigAlgOID();
}
