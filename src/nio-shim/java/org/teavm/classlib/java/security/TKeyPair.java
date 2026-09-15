package org.teavm.classlib.java.security;

import java.security.PrivateKey;
import java.security.PublicKey;

/** See TKey. */
public class TKeyPair {

	private final PublicKey publicKey;
	private final PrivateKey privateKey;

	public TKeyPair(PublicKey publicKey, PrivateKey privateKey) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}

	public PublicKey getPublic() {
		return publicKey;
	}

	public PrivateKey getPrivate() {
		return privateKey;
	}
}
