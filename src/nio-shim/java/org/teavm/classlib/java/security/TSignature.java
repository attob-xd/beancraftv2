package org.teavm.classlib.java.security;

/** Missing from TeaVM's class library. See TKey - the JCA cannot run here. */
public abstract class TSignature {

	protected TSignature() {
	}

	public static TSignature getInstance(String algorithm) {
		throw new UnsupportedOperationException(
				"The JCA is not available under TeaVM; no " + algorithm + " signature");
	}

	public abstract void update(byte[] data);

	public abstract boolean verify(byte[] signature);

	public abstract byte[] sign();
}
