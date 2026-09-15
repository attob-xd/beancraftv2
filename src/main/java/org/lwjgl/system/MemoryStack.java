package org.lwjgl.system;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;

/**
 * Replaces LWJGL's stack allocator.
 *
 * On a desktop this is a per-thread slab of native memory that callers carve scratch buffers
 * out of and release all at once by closing the stack - which is why vanilla always uses it
 * in try-with-resources. Here each malloc is an ordinary heap buffer and close() releases
 * nothing, because the collector already will.
 *
 * The shape is kept exactly: stackPush() still returns something closeable, so vanilla's
 * try-with-resources blocks compile and read the same. What is lost is the pooling, not the
 * correctness - a caller that keeps a buffer past the close would be reading reused memory
 * on a desktop and gets its own data here.
 */
public final class MemoryStack implements AutoCloseable {

	private static final MemoryStack INSTANCE = new MemoryStack();

	private MemoryStack() {
	}

	/** One thread, so one stack; there is nothing to key it by. */
	public static MemoryStack stackPush() {
		return INSTANCE;
	}

	public static MemoryStack stackGet() {
		return INSTANCE;
	}

	public MemoryStack push() {
		return this;
	}

	public MemoryStack pop() {
		return this;
	}

	public ByteBuffer malloc(int size) {
		return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
	}

	public ByteBuffer calloc(int size) {
		return malloc(size);
	}

	public IntBuffer mallocInt(int size) {
		return malloc(size << 2).asIntBuffer();
	}

	public IntBuffer callocInt(int size) {
		return mallocInt(size);
	}

	public PointerBuffer mallocPointer(int size) {
		return PointerBuffer.allocateDirect(size);
	}

	public PointerBuffer callocPointer(int size) {
		return mallocPointer(size);
	}

	/** Nothing to unwind; see the class comment. */
	@Override
	public void close() {
	}
}
