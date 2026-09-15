package org.teavm.classlib.sun.misc;

import java.lang.reflect.Field;

/**
 * Missing from TeaVM's class library, and rightly so - there is no unsafe memory to reach,
 * no object field offsets, and no way to write past the end of an array even if you wanted.
 *
 * guava and netty both probe for Unsafe in a static initialiser and fall back when it is
 * absent: guava's AbstractFuture picks SafeAtomicHelper over UnsafeAtomicHelper, netty's
 * PlatformDependent0 reports hasUnsafe() false. That fallback is a supported path - those
 * libraries run on Android and on JVMs that lock Unsafe down - and it is the one this build
 * takes.
 *
 * The methods exist only so the probing code links. getUnsafe() throws, which is what the
 * probes catch, so nothing below it is ever reached. Each one throws rather than returning
 * a plausible zero: a silent wrong answer from Unsafe would corrupt whatever data structure
 * trusted it, and a loud failure names the caller instead.
 */
public final class TUnsafe {

	private TUnsafe() {
	}

	/**
	 * Real Unsafe refuses this to callers outside the boot class loader, and every library
	 * that wants it is written to handle the refusal. Here it always refuses.
	 */
	public static TUnsafe getUnsafe() {
		throw nope();
	}

	private static UnsupportedOperationException nope() {
		return new UnsupportedOperationException("sun.misc.Unsafe does not exist under TeaVM");
	}

	// --- field offsets and array layout: there is no object layout to describe ---

	public long objectFieldOffset(Field field) {
		throw nope();
	}

	public long staticFieldOffset(Field field) {
		throw nope();
	}

	public Object staticFieldBase(Field field) {
		throw nope();
	}

	public int arrayBaseOffset(Class<?> arrayClass) {
		throw nope();
	}

	public int arrayIndexScale(Class<?> arrayClass) {
		throw nope();
	}

	public int addressSize() {
		throw nope();
	}

	public int pageSize() {
		throw nope();
	}

	// --- raw memory ---

	public long allocateMemory(long bytes) {
		throw nope();
	}

	public long reallocateMemory(long address, long bytes) {
		throw nope();
	}

	public void freeMemory(long address) {
		throw nope();
	}

	public void setMemory(long address, long bytes, byte value) {
		throw nope();
	}

	public void copyMemory(long src, long dst, long bytes) {
		throw nope();
	}

	// --- field access by offset ---

	public boolean getBoolean(Object o, long offset) {
		throw nope();
	}

	public byte getByte(Object o, long offset) {
		throw nope();
	}

	public char getChar(Object o, long offset) {
		throw nope();
	}

	public short getShort(Object o, long offset) {
		throw nope();
	}

	public int getInt(Object o, long offset) {
		throw nope();
	}

	public long getLong(Object o, long offset) {
		throw nope();
	}

	public float getFloat(Object o, long offset) {
		throw nope();
	}

	public double getDouble(Object o, long offset) {
		throw nope();
	}

	public Object getObject(Object o, long offset) {
		throw nope();
	}

	public void putBoolean(Object o, long offset, boolean value) {
		throw nope();
	}

	public void putByte(Object o, long offset, byte value) {
		throw nope();
	}

	public void putChar(Object o, long offset, char value) {
		throw nope();
	}

	public void putShort(Object o, long offset, short value) {
		throw nope();
	}

	public void putInt(Object o, long offset, int value) {
		throw nope();
	}

	public void putLong(Object o, long offset, long value) {
		throw nope();
	}

	public void putFloat(Object o, long offset, float value) {
		throw nope();
	}

	public void putDouble(Object o, long offset, double value) {
		throw nope();
	}

	public void putObject(Object o, long offset, Object value) {
		throw nope();
	}

	public void putOrderedInt(Object o, long offset, int value) {
		throw nope();
	}

	public void putOrderedLong(Object o, long offset, long value) {
		throw nope();
	}

	public void putOrderedObject(Object o, long offset, Object value) {
		throw nope();
	}

	// --- compare and swap ---

	public boolean compareAndSwapInt(Object o, long offset, int expected, int value) {
		throw nope();
	}

	public boolean compareAndSwapLong(Object o, long offset, long expected, long value) {
		throw nope();
	}

	public boolean compareAndSwapObject(Object o, long offset, Object expected, Object value) {
		throw nope();
	}

	// --- misc ---

	public Object allocateInstance(Class<?> cls) {
		throw nope();
	}

	public void throwException(Throwable t) {
		throw nope();
	}

	/*
	 * The fences are the one exception: they order memory operations, and with a single
	 * thread there is nothing to order. Doing nothing is correct, not a stub.
	 */

	public void loadFence() {
	}

	public void storeFence() {
	}

	public void fullFence() {
	}
}
