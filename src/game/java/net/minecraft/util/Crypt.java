package net.minecraft.util;

import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * Replaces Minecraft's online-mode crypto.
 *
 * Vanilla uses this for the session handshake with Mojang's auth servers: an RSA key pair,
 * an AES secret encrypted with the server's public key, and a SHA-1 digest of the server
 * id. Every one of those goes through the JCA - Cipher.getInstance, KeyPairGenerator,
 * MessageDigest - and the JCA loads its providers reflectively. sun.security.jca's
 * ProviderConfig calls Class.newInstance(), which TeaVM has to model as "any class may be
 * instantiated": that one call is what kept Swing, java2d and the image codecs reachable
 * after netty and JNDI had been cut, and it was the last of the three.
 *
 * EaglercraftX never runs this handshake. A browser cannot reach sessionserver.mojang.com
 * with a valid session anyway, so servers run offline-mode and identify players by the
 * name and brand UUID EaglercraftX sends over its own plugin channel; the connection
 * itself is already encrypted, because it is wss://. The vanilla login handlers that would
 * call these are replaced by net.lax1dude.eaglercraft.sp.socket and GuiConnecting.
 *
 * Each method throws rather than returning something plausible, so that if a code path
 * ever does reach the online-mode handshake it fails where the mistake is, instead of
 * negotiating a connection with an all-zero key.
 */
public class Crypt {

	private static CryptException unsupported() {
		return new CryptException(new UnsupportedOperationException(
				"EaglercraftX does not implement Minecraft's online-mode encryption;"
						+ " the transport is already encrypted by the browser"));
	}

	public static SecretKey generateSecretKey() throws CryptException {
		throw unsupported();
	}

	/**
	 * The one method here that must not throw.
	 *
	 * <p>{@code MinecraftServer.initializeKeyPair()} calls this unconditionally while starting,
	 * and wraps any failure in {@code IllegalStateException("Failed to generate key pair")}. So
	 * throwing here does not decline online mode - it stops the server booting at all, which is
	 * what killed Singleplayer the moment a world was created, several steps inside
	 * {@code IntegratedServer.initServer()}.
	 *
	 * <p>The pair is only ever *used* when a server authenticates players: vanilla's
	 * {@code ServerLoginPacketListenerImpl} reads the public key to build an encryption request,
	 * and only when {@code usesAuthentication()} is true. An integrated server answers false, and
	 * a browser cannot reach Mojang's session servers anyway, so nothing in this build reads
	 * either key.
	 *
	 * <p>Hence a pair of placeholders. They describe themselves honestly - RSA, in the formats
	 * the real ones use - but throw from {@code getEncoded()}, which is the only way their bytes
	 * could reach a wire. That keeps the property the rest of this class is built on: a code path
	 * that genuinely reaches the online-mode handshake fails where the mistake is, rather than
	 * negotiating a connection with an all-zero key.
	 */
	public static KeyPair generateKeyPair() throws CryptException {
		return new KeyPair(new PlaceholderPublicKey(), new PlaceholderPrivateKey());
	}

	/** See generateKeyPair. Never read; throws if it ever is. */
	private static final class PlaceholderPublicKey implements PublicKey {
		private static final long serialVersionUID = 1L;

		@Override
		public String getAlgorithm() {
			return "RSA";
		}

		@Override
		public String getFormat() {
			return "X.509";
		}

		@Override
		public byte[] getEncoded() {
			throw new UnsupportedOperationException("EaglercraftX generates no real server key;"
					+ " something is attempting Minecraft's online-mode handshake, which this"
					+ " build does not implement");
		}
	}

	/** See generateKeyPair. Never read; throws if it ever is. */
	private static final class PlaceholderPrivateKey implements PrivateKey {
		private static final long serialVersionUID = 1L;

		@Override
		public String getAlgorithm() {
			return "RSA";
		}

		@Override
		public String getFormat() {
			return "PKCS#8";
		}

		@Override
		public byte[] getEncoded() {
			throw new UnsupportedOperationException("EaglercraftX generates no real server key;"
					+ " something is attempting Minecraft's online-mode handshake, which this"
					+ " build does not implement");
		}
	}

	public static byte[] digestData(String serverId, PublicKey publicKey, SecretKey secretKey)
			throws CryptException {
		throw unsupported();
	}

	public static PublicKey byteToPublicKey(byte[] encoded) throws CryptException {
		throw unsupported();
	}

	public static SecretKey decryptByteToSecretKey(PrivateKey privateKey, byte[] encrypted)
			throws CryptException {
		throw unsupported();
	}

	public static byte[] encryptUsingKey(Key key, byte[] data) throws CryptException {
		throw unsupported();
	}

	public static byte[] decryptUsingKey(Key key, byte[] data) throws CryptException {
		throw unsupported();
	}

	public static Cipher getCipher(int mode, Key key) throws CryptException {
		throw unsupported();
	}
}
