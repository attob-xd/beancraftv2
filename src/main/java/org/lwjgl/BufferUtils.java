package org.lwjgl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Replaces LWJGL's buffer factory. Same answer as MemoryUtil: TeaVM's direct buffers, in the
 * CPU's byte order because WebGL reads them that way.
 */
public final class BufferUtils {

	private BufferUtils() {
	}

	public static ByteBuffer createByteBuffer(int capacity) {
		return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
	}

	public static IntBuffer createIntBuffer(int capacity) {
		return createByteBuffer(capacity << 2).asIntBuffer();
	}

	public static FloatBuffer createFloatBuffer(int capacity) {
		return createByteBuffer(capacity << 2).asFloatBuffer();
	}
}
