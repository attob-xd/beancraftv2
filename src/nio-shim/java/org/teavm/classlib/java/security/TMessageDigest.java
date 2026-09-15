package org.teavm.classlib.java.security;

import net.lax1dude.eaglercraft.crypto.MD5Digest;
import net.lax1dude.eaglercraft.crypto.SHA1Digest;
import net.lax1dude.eaglercraft.crypto.SHA256Digest;

/**
 * Missing from TeaVM's class library, and it has to actually work.
 *
 * <p>The JCA's provider lookup is reflective and cannot run here (see TKey), so an earlier
 * version of this class threw from {@link #getInstance} on the reasoning that failing loudly
 * beats hashing wrongly. The reasoning was right and the conclusion was wrong: a digest is not
 * something this build can decline to have. Guava's {@code Hashing.md5()} builds its
 * {@code HashFunction} in a static initialiser, and 1.18.2's
 * {@code XoroshiroRandomSource.XoroshiroPositionalRandomFactory} holds one - so the first time
 * world generation forked a positional random source, that class initialiser threw and took
 * the client down with "Ticking screen". The same applies to {@code Hashing.sha1()}, which
 * {@code AbstractClientPlayer} uses to name skin textures.
 *
 * <p>EaglercraftX already carries pure-Java MD5, SHA-1 and SHA-256 - lax1dude's Bouncy
 * Castle-derived digests in {@code net.lax1dude.eaglercraft.crypto}, used for its own protocol
 * work - so the honest answer is to hand those out. They are real implementations rather than
 * approximations, which matters more than it might look: MD5 here seeds terrain features, so a
 * hash that merely *looked* random would quietly generate a different world from the one the
 * same seed produces on a desktop client.
 *
 * <p>Anything outside those three still throws, because inventing a digest is exactly the
 * failure mode the original comment was right to avoid.
 */
public abstract class TMessageDigest {

	private final String algorithm;

	protected TMessageDigest() {
		this("unknown");
	}

	protected TMessageDigest(String algorithm) {
		this.algorithm = algorithm;
	}

	public static TMessageDigest getInstance(String algorithm) {
		String name = algorithm == null ? "" : algorithm.replace("-", "").toUpperCase();
		if (name.equals("MD5")) {
			return new Md5();
		}
		if (name.equals("SHA1") || name.equals("SHA")) {
			return new Sha1();
		}
		if (name.equals("SHA256")) {
			return new Sha256();
		}
		throw new UnsupportedOperationException(
				"The JCA is not available under TeaVM; no " + algorithm + " digest");
	}

	public final String getAlgorithm() {
		return algorithm;
	}

	public abstract int getDigestLength();

	public abstract void update(byte input);

	public abstract void update(byte[] input, int offset, int length);

	public void update(byte[] input) {
		update(input, 0, input.length);
	}

	public abstract byte[] digest();

	public byte[] digest(byte[] input) {
		update(input, 0, input.length);
		return digest();
	}

	public abstract void reset();

	@Override
	public String toString() {
		return algorithm + " Message Digest";
	}

	/*
	 * The three concrete digests. Each wraps one of EaglercraftX's implementations, and
	 * digest() finishes into a fresh array and resets - that reset is part of the contract
	 * callers depend on, because Guava's MessageDigestHashFunction hashes repeatedly through a
	 * single instance and would otherwise accumulate every previous input.
	 */
	private static final class Md5 extends TMessageDigest {
		private final MD5Digest impl = new MD5Digest();

		Md5() {
			super("MD5");
		}

		@Override
		public int getDigestLength() {
			return impl.getDigestSize();
		}

		@Override
		public void update(byte input) {
			impl.update(input);
		}

		@Override
		public void update(byte[] input, int offset, int length) {
			impl.update(input, offset, length);
		}

		@Override
		public byte[] digest() {
			byte[] out = new byte[impl.getDigestSize()];
			impl.doFinal(out, 0);
			return out;
		}

		@Override
		public void reset() {
			impl.reset();
		}
	}

	private static final class Sha1 extends TMessageDigest {
		private final SHA1Digest impl = new SHA1Digest();

		Sha1() {
			super("SHA-1");
		}

		@Override
		public int getDigestLength() {
			return impl.getDigestSize();
		}

		@Override
		public void update(byte input) {
			impl.update(input);
		}

		@Override
		public void update(byte[] input, int offset, int length) {
			impl.update(input, offset, length);
		}

		@Override
		public byte[] digest() {
			byte[] out = new byte[impl.getDigestSize()];
			impl.doFinal(out, 0);
			return out;
		}

		@Override
		public void reset() {
			impl.reset();
		}
	}

	private static final class Sha256 extends TMessageDigest {
		private final SHA256Digest impl = new SHA256Digest();

		/** SHA256Digest is the one of the three without a getDigestSize(); SHA-256 is 32 bytes. */
		private static final int SIZE = 32;

		Sha256() {
			super("SHA-256");
		}

		@Override
		public int getDigestLength() {
			return SIZE;
		}

		@Override
		public void update(byte input) {
			impl.update(input);
		}

		@Override
		public void update(byte[] input, int offset, int length) {
			impl.update(input, offset, length);
		}

		@Override
		public byte[] digest() {
			byte[] out = new byte[SIZE];
			impl.doFinal(out, 0);
			return out;
		}

		@Override
		public void reset() {
			impl.reset();
		}
	}
}
