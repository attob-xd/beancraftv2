package org.teavm.classlib.java.security.cert;

import java.security.cert.Certificate;

/** See TCertificate; netty names this while initialising. */
public abstract class TX509Certificate extends Certificate {

	protected TX509Certificate() {
		super("X.509");
	}
}
