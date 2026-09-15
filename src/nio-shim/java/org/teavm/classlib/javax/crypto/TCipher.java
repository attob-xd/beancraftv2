package org.teavm.classlib.javax.crypto;

/**
 * Missing from TeaVM's class library. Named by Connection.setEncryptionKey, which vanilla's
 * login handler calls - so the type must resolve even though EaglercraftX never negotiates
 * Minecraft's own encryption (the transport is already wss://; see net.minecraft.util.Crypt).
 */
public abstract class TCipher {

	public static final int ENCRYPT_MODE = 1;
	public static final int DECRYPT_MODE = 2;

	protected TCipher() {
	}

	public static TCipher getInstance(String transformation) {
		throw new UnsupportedOperationException(
				"The JCA is not available under TeaVM; no " + transformation + " cipher");
	}

	public abstract byte[] update(byte[] input);

	public abstract byte[] doFinal(byte[] input);

	public abstract int getBlockSize();
}
