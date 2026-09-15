package org.teavm.classlib.java.util;

import net.lax1dude.eaglercraft.EaglercraftUUID;

/**
 * TeaVM's own UUID is a stub: it holds a String, and has no (long, long) constructor, no
 * getMostSignificantBits, and no nameUUIDFromBytes. Minecraft cannot start without those -
 * GameProfile is built on them, and so is every player identity in the game.
 *
 * EaglercraftUUID already implements all of it correctly, MD5-based name UUIDs included, so
 * this delegates rather than writing the arithmetic a second time. The two types stay
 * interchangeable, which matters because EaglercraftX's own code passes EaglercraftUUID
 * where vanilla passes java.util.UUID.
 */
public final class TUUID implements Comparable<TUUID> {

	private final long msb;
	private final long lsb;

	public TUUID(long mostSigBits, long leastSigBits) {
		this.msb = mostSigBits;
		this.lsb = leastSigBits;
	}

	public long getMostSignificantBits() {
		return msb;
	}

	public long getLeastSignificantBits() {
		return lsb;
	}

	public static TUUID randomUUID() {
		EaglercraftUUID u = EaglercraftUUID.randomUUID();
		return new TUUID(u.getMostSignificantBits(), u.getLeastSignificantBits());
	}

	public static TUUID nameUUIDFromBytes(byte[] name) {
		EaglercraftUUID u = EaglercraftUUID.nameUUIDFromBytes(name);
		return new TUUID(u.getMostSignificantBits(), u.getLeastSignificantBits());
	}

	public static TUUID fromString(String name) {
		EaglercraftUUID u = EaglercraftUUID.fromString(name);
		return new TUUID(u.getMostSignificantBits(), u.getLeastSignificantBits());
	}

	public int version() {
		return (int) ((msb >> 12) & 0x0f);
	}

	public int variant() {
		return (int) ((lsb >>> (64 - (lsb >>> 62))) & (lsb >> 63));
	}

	public long timestamp() {
		if (version() != 1) {
			throw new UnsupportedOperationException("Not a time-based UUID");
		}
		return (msb & 0x0FFFL) << 48 | ((msb >> 16) & 0x0FFFFL) << 32 | msb >>> 32;
	}

	public int clockSequence() {
		if (version() != 1) {
			throw new UnsupportedOperationException("Not a time-based UUID");
		}
		return (int) ((lsb & 0x3FFF000000000000L) >>> 48);
	}

	public long node() {
		if (version() != 1) {
			throw new UnsupportedOperationException("Not a time-based UUID");
		}
		return lsb & 0x0000FFFFFFFFFFFFL;
	}

	@Override
	public String toString() {
		return new EaglercraftUUID(msb, lsb).toString();
	}

	@Override
	public int hashCode() {
		long hilo = msb ^ lsb;
		return ((int) (hilo >> 32)) ^ (int) hilo;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TUUID)) {
			return false;
		}
		TUUID other = (TUUID) obj;
		return msb == other.msb && lsb == other.lsb;
	}

	@Override
	public int compareTo(TUUID val) {
		// unsigned comparison, most significant half first, as java.util.UUID defines it
		return msb < val.msb ? -1
				: msb > val.msb ? 1
				: lsb < val.lsb ? -1
				: lsb > val.lsb ? 1 : 0;
	}
}
