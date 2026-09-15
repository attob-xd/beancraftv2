package org.lwjgl.system;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Replaces LWJGL's off-heap allocator.
 *
 * LWJGL hands out ByteBuffers that point at malloc'd memory, and every method here is a
 * native call. There is no off-heap memory in a browser, so the buffers below are ordinary
 * direct buffers from TeaVM's own java.nio - which is heap-backed underneath, and is the
 * same thing EaglercraftX's own allocator hands out.
 *
 * That has one visible consequence: an address is not a number here. memAlloc and friends
 * work because the caller only ever uses the Buffer they get back; getAllocator, memAddress0
 * and memByteBuffer trade in raw longs, and those three throw rather than invent a pointer.
 * The only caller of all three is blaze3d's MemoryTracker, which this port replaces with a
 * version that stays in Buffer terms (see src/game/java).
 *
 * Freeing is a no-op. The collector owns these buffers, so memFree cannot do anything useful
 * and must not do anything harmful - a caller that frees and then reads would, on a desktop,
 * be reading freed memory; here it reads its own data, which is the safer of the two wrongs.
 */
public final class MemoryUtil {

	private MemoryUtil() {
	}

	/**
	 * Named by blaze3d's MemoryTracker; see the class comment for why it cannot work.
	 *
	 * The full method set is kept even though nothing here calls it, because LWJGL's own
	 * allocators still implement this interface in the jar - MemoryManage's two and
	 * jemalloc's - and dropping methods would leave those classes abstract.
	 */
	public interface MemoryAllocator {

		long getMalloc();

		long getCalloc();

		long getRealloc();

		long getFree();

		long getAlignedAlloc();

		long getAlignedFree();

		long malloc(long size);

		long calloc(long num, long size);

		long realloc(long ptr, long size);

		void free(long ptr);

		long aligned_alloc(long alignment, long size);

		void aligned_free(long ptr);
	}

	/**
	 * LWJGL's debug allocator reports leaks through this. Nothing here allocates natively so
	 * there is nothing to report, but MemoryManage in the jar still names both types, and a
	 * class TeaVM cannot resolve is a load-time error rather than a lazy one - so they exist.
	 */
	public interface MemoryAllocationReport {

		enum Aggregate {
			ALL,
			GROUP_BY_METHOD,
			GROUP_BY_STACKTRACE
		}

		void invoke(long address, long memory, long threadId, String threadName,
				StackTraceElement... stacktrace);
	}

	private static UnsupportedOperationException noAddresses() {
		return new UnsupportedOperationException(
				"There are no memory addresses in a browser; use the Buffer overloads");
	}

	public static MemoryAllocator getAllocator(boolean debugAllocator) {
		throw noAddresses();
	}

	public static MemoryAllocator getAllocator() {
		throw noAddresses();
	}

	public static long memAddress0(Buffer buffer) {
		throw noAddresses();
	}

	public static long memAddress(Buffer buffer) {
		throw noAddresses();
	}

	public static ByteBuffer memByteBuffer(long address, int capacity) {
		throw noAddresses();
	}

	public static void memSet(long address, int value, long bytes) {
		throw noAddresses();
	}

	/*
	 * The allocating half. Native order matters: vanilla writes vertex data through these
	 * buffers and hands them straight to WebGL, which reads them in the CPU's order.
	 */

	public static ByteBuffer memAlloc(int size) {
		return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
	}

	public static ByteBuffer memCalloc(int size) {
		// allocateDirect already zeroes, which is the only difference from memAlloc.
		return memAlloc(size);
	}

	public static IntBuffer memAllocInt(int size) {
		return memAlloc(size << 2).asIntBuffer();
	}

	public static FloatBuffer memAllocFloat(int size) {
		return memAlloc(size << 2).asFloatBuffer();
	}

	/**
	 * A real memRealloc can grow in place. This one always copies, which is correct but
	 * slower; the old buffer stays valid instead of being freed, and nothing in vanilla
	 * looks at it again.
	 *
	 * <p>The returned buffer is positioned at 0 with its limit at its capacity, which is what
	 * LWJGL's own memRealloc gives back and what every caller here assumes. An earlier version
	 * carried the old buffer's position and limit across:
	 *
	 * <pre>    next.position(Math.min(old.position(), size));
	 *    next.limit(Math.min(old.limit(), size));</pre>
	 *
	 * <p>which looks like careful preservation and is the opposite. Growing a buffer is the only
	 * reason to call this, so the old limit is always the <i>smaller</i> one - the new buffer
	 * came back with the capacity that was asked for and the limit it was trying to escape.
	 *
	 * <p>That had teeth, because {@code BufferBuilder.ensureCapacity} tests
	 * {@code buffer.capacity()} while {@code ByteBuffer.putShort} bounds-checks against
	 * {@code limit}: the builder was satisfied it had 2 MB and the very next write was refused
	 * at byte 1536. So <b>every BufferBuilder that outgrew its initial allocation threw</b> -
	 * a skin preview reaches that in one model, and a chunk mesh passes 1 KB immediately, so
	 * this sat directly under world rendering as well. It surfaced as
	 * {@code IndexOutOfBoundsException: Index 1536 is outside of range [0;1535)} from inside
	 * {@code ModelPart.render}, which names neither this method nor a buffer size that means
	 * anything until the "Needed to grow BufferBuilder buffer" debug line above it is read.
	 */
	public static ByteBuffer memRealloc(ByteBuffer old, int size) {
		ByteBuffer next = memAlloc(size);
		if (old != null) {
			int keep = Math.min(size, old.capacity());
			// Absolute puts and gets, so neither buffer's position moves and `next` is left
			// where memAlloc put it: position 0, limit == capacity.
			for (int i = 0; i < keep; ++i) {
				next.put(i, old.get(i));
			}
		}
		return next;
	}

	/** The collector owns these buffers; see the class comment. */
	public static void memFree(Buffer buffer) {
	}

	/**
	 * Reads a NUL-terminated ASCII string. blaze3d uses it on the bytes WebGL returns for
	 * glGetString-style queries, which really are ASCII.
	 */
	public static String memASCII(ByteBuffer buffer, int length) {
		StringBuilder sb = new StringBuilder(length);
		int base = buffer.position();
		for (int i = 0; i < length; ++i) {
			sb.append((char) (buffer.get(base + i) & 0xFF));
		}
		return sb.toString();
	}

	public static String memASCII(ByteBuffer buffer) {
		return memASCII(buffer, buffer.remaining());
	}

	public static String memUTF8(ByteBuffer buffer) {
		return memASCII(buffer);
	}
}
