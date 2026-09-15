package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Replaces LWJGL's buffer of native pointers.
 *
 * There are no pointers here (see MemoryUtil), so this carries two parallel things: a long
 * per slot, which is all a caller that only compares or passes handles ever needs, and an
 * object per slot, which is how this port actually returns a buffer or a struct where LWJGL
 * would have returned an address.
 *
 * That split is deliberate. glfwGetMonitors fills the long slots - vanilla's ScreenManager
 * only uses them as opaque monitor handles, and 0 is as good a handle as any pointer. STB's
 * vorbis decoder instead asks for the thing at a slot with getFloatBuffer/getPointerBuffer,
 * and those read the object slots, so the data comes back as itself rather than as a number
 * this build would have to invent.
 */
public class PointerBuffer {

	private final long[] pointers;
	private final Object[] objects;
	private int position;
	private int limit;

	public PointerBuffer(int capacity) {
		this.pointers = new long[capacity];
		this.objects = new Object[capacity];
		this.limit = capacity;
	}

	public static PointerBuffer allocateDirect(int capacity) {
		return new PointerBuffer(capacity);
	}

	public int capacity() {
		return pointers.length;
	}

	public int position() {
		return position;
	}

	public PointerBuffer position(int newPosition) {
		this.position = newPosition;
		return this;
	}

	public int limit() {
		return limit;
	}

	public PointerBuffer limit(int newLimit) {
		this.limit = newLimit;
		return this;
	}

	public int remaining() {
		return limit - position;
	}

	public long get(int index) {
		return pointers[index];
	}

	public long get() {
		return pointers[position++];
	}

	public PointerBuffer put(int index, long value) {
		pointers[index] = value;
		return this;
	}

	public PointerBuffer put(long value) {
		pointers[position++] = value;
		return this;
	}

	/** The object half; see the class comment. */
	public PointerBuffer putObject(int index, Object value) {
		objects[index] = value;
		return this;
	}

	public Object getObject(int index) {
		return objects[index];
	}

	public PointerBuffer getPointerBuffer(int index) {
		return (PointerBuffer) objects[index];
	}

	public ByteBuffer getByteBuffer(int index, int size) {
		Object o = objects[index];
		if (o instanceof ByteBuffer) {
			return (ByteBuffer) o;
		}
		return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
	}

	public FloatBuffer getFloatBuffer(int index, int size) {
		Object o = objects[index];
		if (o instanceof FloatBuffer) {
			return (FloatBuffer) o;
		}
		return ByteBuffer.allocateDirect(size << 2).order(ByteOrder.nativeOrder()).asFloatBuffer();
	}
}
