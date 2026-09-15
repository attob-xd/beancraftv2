package org.teavm.classlib.java.security;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/** Missing from TeaVM's class library. See TKey - the JCA cannot run here. */
public abstract class TKeyFactory {

	protected TKeyFactory() {
	}

	public static TKeyFactory getInstance(String algorithm) {
		throw new UnsupportedOperationException(
				"The JCA is not available under TeaVM; no " + algorithm + " key factory");
	}

	public abstract PublicKey generatePublic(X509EncodedKeySpec spec);
}
