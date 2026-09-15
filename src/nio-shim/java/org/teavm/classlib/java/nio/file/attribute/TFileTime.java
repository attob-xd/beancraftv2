package org.teavm.classlib.java.nio.file.attribute;

import java.util.concurrent.TimeUnit;

public final class TFileTime implements Comparable<TFileTime> {

	private final long millis;

	private TFileTime(long millis) {
		this.millis = millis;
	}

	public static TFileTime fromMillis(long value) {
		return new TFileTime(value);
	}

	public static TFileTime from(long value, TimeUnit unit) {
		return new TFileTime(unit.toMillis(value));
	}

	public long toMillis() {
		return millis;
	}

	public long to(TimeUnit unit) {
		return unit.convert(millis, TimeUnit.MILLISECONDS);
	}

	@Override
	public int compareTo(TFileTime other) {
		return Long.compare(millis, other.millis);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof TFileTime && ((TFileTime) o).millis == millis;
	}

	@Override
	public int hashCode() {
		return (int) (millis ^ (millis >>> 32));
	}

	@Override
	public String toString() {
		return Long.toString(millis);
	}
}
