package net.minecraft.util;

import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import org.apache.commons.lang3.Validate;

/**
 * Vanilla's class, reproduced with one change: Integer.toUnsignedLong is written out.
 *
 * TeaVM's class library has no Integer.toUnsignedLong, and cellIndex - the method every read
 * and every write of block state, biome and light data goes through - calls it twice. This is
 * the second and last caller in vanilla; the other is
 * net.minecraft.world.level.levelgen.XoroshiroRandomSource, whose header explains why the
 * method is written out here rather than added to java.lang.Integer.
 *
 * toUnsignedLong(i) is (i &amp; 0xFFFFFFFFL) - the JDK's own implementation - so the arithmetic
 * is bit-for-bit vanilla's. That was worth being sure about rather than confident about: a
 * wrong cell index does not throw, it silently reads a neighbouring block's bits, so the
 * failure would surface as corrupt chunks a long way from here. The reproduction was checked
 * against the class in 1.18.2-mapped.jar by tools/VerifyBitStorage.java, which packs random
 * values at every bit width from 1 to 32 and compares the raw longs, every get, every
 * getAndSet return, getAll and unpack.
 *
 * MAGIC is vanilla's table unchanged, re-laid-out as the (multiplier, addend, shift) triples
 * it actually is - indexed by 3 * (valuesPerLong - 1) - and extracted mechanically from the
 * jar rather than retyped.
 */
public class SimpleBitStorage implements BitStorage {

	private static final int[] MAGIC = new int[] {
			-1, -1, 0,
			Integer.MIN_VALUE, 0, 0,
			1431655765, 1431655765, 0,
			Integer.MIN_VALUE, 0, 1,
			858993459, 858993459, 0,
			715827882, 715827882, 0,
			613566756, 613566756, 0,
			Integer.MIN_VALUE, 0, 2,
			477218588, 477218588, 0,
			429496729, 429496729, 0,
			390451572, 390451572, 0,
			357913941, 357913941, 0,
			330382099, 330382099, 0,
			306783378, 306783378, 0,
			286331153, 286331153, 0,
			Integer.MIN_VALUE, 0, 3,
			252645135, 252645135, 0,
			238609294, 238609294, 0,
			226050910, 226050910, 0,
			214748364, 214748364, 0,
			204522252, 204522252, 0,
			195225786, 195225786, 0,
			186737708, 186737708, 0,
			178956970, 178956970, 0,
			171798691, 171798691, 0,
			165191049, 165191049, 0,
			159072862, 159072862, 0,
			153391689, 153391689, 0,
			148102320, 148102320, 0,
			143165576, 143165576, 0,
			138547332, 138547332, 0,
			Integer.MIN_VALUE, 0, 4,
			130150524, 130150524, 0,
			126322567, 126322567, 0,
			122713351, 122713351, 0,
			119304647, 119304647, 0,
			116080197, 116080197, 0,
			113025455, 113025455, 0,
			110127366, 110127366, 0,
			107374182, 107374182, 0,
			104755299, 104755299, 0,
			102261126, 102261126, 0,
			99882960, 99882960, 0,
			97612893, 97612893, 0,
			95443717, 95443717, 0,
			93368854, 93368854, 0,
			91382282, 91382282, 0,
			89478485, 89478485, 0,
			87652393, 87652393, 0,
			85899345, 85899345, 0,
			84215045, 84215045, 0,
			82595524, 82595524, 0,
			81037118, 81037118, 0,
			79536431, 79536431, 0,
			78090314, 78090314, 0,
			76695844, 76695844, 0,
			75350303, 75350303, 0,
			74051160, 74051160, 0,
			72796055, 72796055, 0,
			71582788, 71582788, 0,
			70409299, 70409299, 0,
			69273666, 69273666, 0,
			68174084, 68174084, 0,
			Integer.MIN_VALUE, 0, 5
	};

	private final long[] data;
	private final int bits;
	private final long mask;
	private final int size;
	private final int valuesPerLong;
	private final int divideMul;
	private final int divideAdd;
	private final int divideShift;

	public SimpleBitStorage(int bits, int size, int[] values) {
		this(bits, size);
		int cell = 0;
		int i;
		for (i = 0; i <= size - this.valuesPerLong; i += this.valuesPerLong) {
			long packed = 0L;
			for (int j = this.valuesPerLong - 1; j >= 0; --j) {
				packed <<= bits;
				packed |= values[i + j] & this.mask;
			}
			this.data[cell++] = packed;
		}

		int remaining = size - i;
		if (remaining > 0) {
			long packed = 0L;
			for (int j = remaining - 1; j >= 0; --j) {
				packed <<= bits;
				packed |= values[i + j] & this.mask;
			}
			this.data[cell] = packed;
		}
	}

	public SimpleBitStorage(int bits, int size) {
		this(bits, size, (long[]) null);
	}

	public SimpleBitStorage(int bits, int size, @Nullable long[] data) {
		Validate.inclusiveBetween(1L, 32L, bits);
		this.size = size;
		this.bits = bits;
		this.mask = (1L << bits) - 1L;
		this.valuesPerLong = (char) (64 / bits);
		int magicIndex = 3 * (this.valuesPerLong - 1);
		this.divideMul = MAGIC[magicIndex + 0];
		this.divideAdd = MAGIC[magicIndex + 1];
		this.divideShift = MAGIC[magicIndex + 2];
		int cells = (size + this.valuesPerLong - 1) / this.valuesPerLong;
		if (data != null) {
			if (data.length != cells) {
				throw new SimpleBitStorage.InitializationException(
						"Invalid length given for storage, got: " + data.length
								+ " but expected: " + cells);
			}
			this.data = data;
		} else {
			this.data = new long[cells];
		}
	}

	/** Vanilla, with Integer.toUnsignedLong written out as (x &amp; 0xFFFFFFFFL). */
	private int cellIndex(int index) {
		long mul = this.divideMul & 0xFFFFFFFFL;
		long add = this.divideAdd & 0xFFFFFFFFL;
		return (int) (index * mul + add >> 32 >> this.divideShift);
	}

	@Override
	public int getAndSet(int index, int value) {
		Validate.inclusiveBetween(0L, this.size - 1, index);
		Validate.inclusiveBetween(0L, this.mask, value);
		int cell = this.cellIndex(index);
		long packed = this.data[cell];
		int shift = (index - cell * this.valuesPerLong) * this.bits;
		int old = (int) (packed >> shift & this.mask);
		this.data[cell] = packed & ~(this.mask << shift) | (value & this.mask) << shift;
		return old;
	}

	@Override
	public void set(int index, int value) {
		Validate.inclusiveBetween(0L, this.size - 1, index);
		Validate.inclusiveBetween(0L, this.mask, value);
		int cell = this.cellIndex(index);
		long packed = this.data[cell];
		int shift = (index - cell * this.valuesPerLong) * this.bits;
		this.data[cell] = packed & ~(this.mask << shift) | (value & this.mask) << shift;
	}

	@Override
	public int get(int index) {
		Validate.inclusiveBetween(0L, this.size - 1, index);
		int cell = this.cellIndex(index);
		long packed = this.data[cell];
		int shift = (index - cell * this.valuesPerLong) * this.bits;
		return (int) (packed >> shift & this.mask);
	}


	@Override
	public long[] getRaw() {
		return this.data;
	}

	@Override
	public int getSize() {
		return this.size;
	}

	@Override
	public int getBits() {
		return this.bits;
	}

	@Override
	public void getAll(IntConsumer consumer) {
		int seen = 0;
		for (long packed : this.data) {
			for (int i = 0; i < this.valuesPerLong; ++i) {
				consumer.accept((int) (packed & this.mask));
				packed >>= this.bits;
				if (++seen >= this.size) {
					return;
				}
			}
		}
	}

	@Override
	public void unpack(int[] out) {
		int cells = this.data.length;
		int at = 0;

		for (int cell = 0; cell < cells - 1; ++cell) {
			long packed = this.data[cell];
			for (int i = 0; i < this.valuesPerLong; ++i) {
				out[at + i] = (int) (packed & this.mask);
				packed >>= this.bits;
			}
			at += this.valuesPerLong;
		}

		int remaining = this.size - at;
		if (remaining > 0) {
			long packed = this.data[cells - 1];
			for (int i = 0; i < remaining; ++i) {
				out[at + i] = (int) (packed & this.mask);
				packed >>= this.bits;
			}
		}
	}

	@Override
	public BitStorage copy() {
		return new SimpleBitStorage(this.bits, this.size, (long[]) this.data.clone());
	}

	public static class InitializationException extends RuntimeException {
		InitializationException(String message) {
			super(message);
		}
	}
}
