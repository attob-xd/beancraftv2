package com.mojang.blaze3d.platform;

import java.nio.ByteBuffer;

import org.lwjgl.system.MemoryUtil;

/**
 * Replaces blaze3d's tracked off-heap allocator.
 *
 * Vanilla asks LWJGL's allocator for a raw address, then wraps it in a ByteBuffer with
 * MemoryUtil.memByteBuffer - the point being that the memory sits outside the Java heap and
 * outside the collector's accounting, so a growing BufferBuilder does not churn the heap.
 *
 * There is no memory outside the heap in a browser, and no address to wrap, so this stays in
 * Buffer terms the whole way. It is the only caller of MemoryUtil.getAllocator, memAddress0
 * and memByteBuffer, and replacing it is what lets those three throw honestly instead of
 * inventing a pointer.
 *
 * The tracking is gone with it. That was a debug feature - it fed the allocation counters on
 * the F3 screen - and the browser is the thing that accounts for this memory now.
 */
public class MemoryTracker {

	public static ByteBuffer create(int size) {
		return MemoryUtil.memAlloc(size);
	}

	/**
	 * Vanilla reallocs in place where it can. This always copies, which is what
	 * MemoryUtil.memRealloc does here; the caller replaces its reference either way.
	 */
	public static ByteBuffer resize(ByteBuffer buffer, int size) {
		return MemoryUtil.memRealloc(buffer, size);
	}
}
