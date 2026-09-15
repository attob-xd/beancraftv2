package net.minecraft.world.level.levelgen;

import java.nio.charset.StandardCharsets;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;

import net.minecraft.util.Mth;

/**
 * Vanilla's class, reproduced with one change: Integer.toUnsignedLong is written out.
 *
 * TeaVM's class library has no Integer.toUnsignedLong, and this is the first thing in the
 * game to want it - BlendedNoise's static initialiser builds a PerlinNoise, which seeds an
 * ImprovedNoise from this generator, all during registry bootstrap. The client died there
 * with NoSuchMethodError before Minecraft was constructed.
 *
 * Replacing java.lang.Integer to add one static method is not a trade worth making: it is
 * reached by every autoboxed int in the build and carries TeaVM's own parsing and cache
 * behaviour. This class and net.minecraft.util.SimpleBitStorage are its only two callers in
 * the whole of vanilla, so they are what gets replaced instead.
 *
 * toUnsignedLong(i) is (i &amp; 0xFFFFFFFFL) - the JDK's own implementation - so the arithmetic
 * below is identical to vanilla's, and the generator produces the same sequence for a given
 * seed. That matters more than usual here: this is the world generator's random source, so a
 * difference would mean a different world from the same seed.
 */
public class XoroshiroRandomSource implements RandomSource {

	private static final float FLOAT_UNIT = 5.9604645E-8F;
	private static final double DOUBLE_UNIT = 1.1102230246251565E-16D;

	private Xoroshiro128PlusPlus randomNumberGenerator;
	private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

	public XoroshiroRandomSource(long seed) {
		this.randomNumberGenerator =
				new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
	}

	public XoroshiroRandomSource(long seedLo, long seedHi) {
		this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
	}

	@Override
	public RandomSource fork() {
		return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(),
				this.randomNumberGenerator.nextLong());
	}

	@Override
	public PositionalRandomFactory forkPositional() {
		return new XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(),
				this.randomNumberGenerator.nextLong());
	}

	@Override
	public void setSeed(long seed) {
		this.randomNumberGenerator =
				new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
		this.gaussianSource.reset();
	}

	@Override
	public int nextInt() {
		return (int) this.randomNumberGenerator.nextLong();
	}

	/**
	 * The JDK 17 bounded-random algorithm, as vanilla uses it. The only edit is the two
	 * Integer.toUnsignedLong calls, written as the mask they are defined to be.
	 */
	@Override
	public int nextInt(int bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("Bound must be positive");
		}
		long l = nextInt() & 0xFFFFFFFFL;
		long m = l * (long) bound;
		long n = m & 0xFFFFFFFFL;
		if (n < (long) bound) {
			int i = Integer.remainderUnsigned(~bound + 1, bound);
			while (n < (long) i) {
				l = nextInt() & 0xFFFFFFFFL;
				m = l * (long) bound;
				n = m & 0xFFFFFFFFL;
			}
		}
		return (int) (m >> 32);
	}

	@Override
	public long nextLong() {
		return this.randomNumberGenerator.nextLong();
	}

	@Override
	public boolean nextBoolean() {
		return (this.randomNumberGenerator.nextLong() & 1L) != 0L;
	}

	@Override
	public float nextFloat() {
		return (float) nextBits(24) * FLOAT_UNIT;
	}

	@Override
	public double nextDouble() {
		return (double) nextBits(53) * DOUBLE_UNIT;
	}

	@Override
	public double nextGaussian() {
		return this.gaussianSource.nextGaussian();
	}

	@Override
	public void consumeCount(int count) {
		for (int i = 0; i < count; ++i) {
			this.randomNumberGenerator.nextLong();
		}
	}

	private long nextBits(int bits) {
		return this.randomNumberGenerator.nextLong() >>> (64 - bits);
	}

	public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {

		private static final HashFunction MD5_128 = Hashing.md5();

		private final long seedLo;
		private final long seedHi;

		public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
			this.seedLo = seedLo;
			this.seedHi = seedHi;
		}

		@Override
		public RandomSource at(int x, int y, int z) {
			long l = Mth.getSeed(x, y, z);
			long m = l ^ this.seedLo;
			return new XoroshiroRandomSource(m, this.seedHi);
		}

		@Override
		public RandomSource fromHashOf(String name) {
			byte[] bytes = MD5_128.hashString(name, StandardCharsets.UTF_8).asBytes();
			long l = Longs.fromBytes(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5],
					bytes[6], bytes[7]);
			long m = Longs.fromBytes(bytes[8], bytes[9], bytes[10], bytes[11], bytes[12],
					bytes[13], bytes[14], bytes[15]);
			return new XoroshiroRandomSource(l ^ this.seedLo, m ^ this.seedHi);
		}

		@Override
		public void parityConfigString(StringBuilder sb) {
			sb.append("seedLo: ").append(this.seedLo).append(", seedHi: ").append(this.seedHi);
		}
	}
}
