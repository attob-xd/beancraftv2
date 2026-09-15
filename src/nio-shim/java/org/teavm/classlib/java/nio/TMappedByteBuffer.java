package org.teavm.classlib.java.nio;

/**
 * Missing from TeaVM's class library. Memory-mapping a file needs an address space shared
 * with the OS, which a page does not have; RegionFile is the only thing that would want
 * one, and FileChannel.map refuses before this could be produced. See TFileChannel.
 *
 * The real MappedByteBuffer extends ByteBuffer, and this does not: TeaVM's ByteBuffer has
 * no constructor a subclass can reach. That difference is invisible here because nothing
 * can obtain an instance to assign anywhere - the type exists only so the metadata that
 * names it resolves at load time.
 */
public abstract class TMappedByteBuffer {

	protected TMappedByteBuffer() {
	}

	public abstract TMappedByteBuffer force();

	public abstract boolean isLoaded();
}
