package org.teavm.classlib.java.security;

import java.util.Random;

/**
 * Missing from TeaVM's class library. The browser has crypto.getRandomValues, but nothing
 * here needs cryptographic strength: the JCA paths that would are refused outright (see
 * TKey), and what remains is world seeds and shuffling. This is an ordinary Random with
 * SecureRandom's name so those references resolve, and it is NOT suitable for anything
 * that actually needs unpredictability.
 */
public class TSecureRandom extends Random {

	public TSecureRandom() {
	}

	public TSecureRandom(byte[] seed) {
	}

	public static TSecureRandom getInstanceStrong() {
		return new TSecureRandom();
	}

	public void setSeed(byte[] seed) {
	}

	public byte[] generateSeed(int numBytes) {
		byte[] out = new byte[numBytes];
		nextBytes(out);
		return out;
	}
}
