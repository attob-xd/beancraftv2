package org.teavm.classlib.java.security.spec;

/** Missing from TeaVM's class library. See java.security.Key - the JCA cannot run here. */
public class TX509EncodedKeySpec {

	private final byte[] encoded;

	public TX509EncodedKeySpec(byte[] encoded) {
		this.encoded = encoded.clone();
	}

	public byte[] getEncoded() {
		return encoded.clone();
	}

	public String getFormat() {
		return "X.509";
	}
}
