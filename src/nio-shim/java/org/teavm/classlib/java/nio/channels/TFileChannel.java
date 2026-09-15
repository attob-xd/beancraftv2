package org.teavm.classlib.java.nio.channels;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.lax1dude.eaglercraft.internal.vfs2.VFile2;

/**
 * Missing from TeaVM's class library, and needed before a world will load: 1.18.2 writes
 * every chunk through RegionFile, which is built on positional FileChannel reads and writes
 * into a .mca file, and takes DirectoryLock on session.lock before it touches a save at all.
 *
 * This used to refuse every call, on the reasoning that VFile2 - the IndexedDB-backed
 * filesystem behind java.io.File here, see TFile - has no seekable accessor. It does not.
 * What it has is getAllBytes and setAllBytes, and that is enough: the channel holds the whole
 * file in a byte[], serves positions out of it, and writes it back. Refusing was the right
 * choice while nothing could reach a world; now that something can, a channel that works on
 * whole files is worth more than one that works on none.
 *
 * Three consequences, stated rather than hidden:
 *
 *   - A file is read and written whole. Opening a 4 MB region file reads 4 MB; force() or
 *     close() writes 4 MB back. RegionFile calls force(true) once per flush rather than once
 *     per chunk, so the cost lands per save - but it is genuinely more I/O than a real
 *     channel does, and a large save will feel it.
 *   - A channel is a snapshot. Two channels open on one path do not see each other's writes,
 *     and the last to flush wins. Nothing in the game opens one file twice: RegionFile keeps
 *     a single channel per region and RegionFileStorage caches them.
 *   - Locks are page-local. A lock exists to keep two processes off one save. A page has no
 *     second process, but it can have a second tab on the same IndexedDB, and this does not
 *     see it - LOCKED below is a set in this page. So DirectoryLock still does its job within
 *     a page and cannot do it between tabs. Doing better needs the Web Locks API, which is
 *     asynchronous and so cannot be answered from inside a synchronous tryLock().
 *
 * Nothing here is mapped: FileChannel.map has no implementation and nothing calls it; see
 * TMappedByteBuffer.
 */
public class TFileChannel implements Closeable {

	public enum MapMode {
		READ_ONLY, READ_WRITE, PRIVATE
	}

	/** Paths locked in this page. See the class comment for what this does not cover. */
	private static final Set<String> LOCKED = new HashSet<>();

	private final String path;
	private final boolean readable;
	private final boolean writable;

	private byte[] data;
	private int length;
	private long position;
	private boolean open = true;
	private boolean dirty;
	private String heldLock;

	private TFileChannel(String path, byte[] initial, boolean readable, boolean writable) {
		this.path = path;
		this.data = initial;
		this.length = initial.length;
		this.readable = readable;
		this.writable = writable;
	}

	/**
	 * The options arrive as this shim's own TStandardOpenOption rather than the JDK's enum,
	 * so they are matched by name instead of by identity; the constant names are the same.
	 */
	public static TFileChannel open(Path path, OpenOption... options) throws IOException {
		if (path == null) {
			throw new NullPointerException("path");
		}
		String name = String.valueOf(path);
		boolean read = false;
		boolean write = false;
		boolean create = false;
		boolean createNew = false;
		boolean truncate = false;
		boolean append = false;
		if (options != null) {
			for (OpenOption option : options) {
				switch (String.valueOf(option)) {
					case "READ":
						read = true;
						break;
					case "WRITE":
						write = true;
						break;
					case "APPEND":
						write = true;
						append = true;
						break;
					case "CREATE":
						create = true;
						break;
					case "CREATE_NEW":
						create = true;
						createNew = true;
						break;
					case "TRUNCATE_EXISTING":
						truncate = true;
						break;
					// SYNC and DSYNC ask for a device flush there is nothing behind, and
					// SPARSE is a hint; see TStandardOpenOption. RegionFile passes DSYNC.
					case "SYNC":
					case "DSYNC":
					case "SPARSE":
						break;
					default:
						throw new UnsupportedOperationException("open option " + option);
				}
			}
		}
		if (!read && !write) {
			read = true;
		}

		VFile2 file = new VFile2(name);
		boolean exists = file.exists();
		if (exists && createNew) {
			throw new FileAlreadyExistsException(name);
		}
		if (!exists && !create) {
			// DirectoryLock.isLocked depends on this: no session.lock means not locked.
			throw new NoSuchFileException(name);
		}
		byte[] initial = exists && !truncate ? file.getAllBytes() : null;
		if (initial == null) {
			initial = new byte[0];
		}
		TFileChannel channel = new TFileChannel(name, initial, read, write);
		if (write && (!exists || truncate)) {
			// An empty file still has to materialise, even if nothing is written to it.
			channel.dirty = true;
		}
		if (append) {
			channel.position = channel.length;
		}
		return channel;
	}

	public int read(ByteBuffer dst) throws IOException {
		int n = read(dst, position);
		if (n > 0) {
			position += n;
		}
		return n;
	}

	public int read(ByteBuffer dst, long at) throws IOException {
		checkOpen();
		if (!readable) {
			throw new NonReadableChannelException();
		}
		if (at < 0L) {
			throw new IllegalArgumentException("Negative position: " + at);
		}
		if (at >= length) {
			return -1;
		}
		int n = (int) Math.min(dst.remaining(), length - at);
		if (n <= 0) {
			return 0;
		}
		dst.put(data, (int) at, n);
		return n;
	}

	public int write(ByteBuffer src) throws IOException {
		int n = write(src, position);
		if (n > 0) {
			position += n;
		}
		return n;
	}

	public int write(ByteBuffer src, long at) throws IOException {
		checkOpen();
		if (!writable) {
			throw new NonWritableChannelException();
		}
		if (at < 0L) {
			throw new IllegalArgumentException("Negative position: " + at);
		}
		int n = src.remaining();
		if (n == 0) {
			return 0;
		}
		ensureCapacity(at + n);
		// A write past the end leaves a hole, which a real file reads back as zeroes. The
		// array can hold anything there after a truncate, so the hole is zeroed explicitly.
		if (at > length) {
			Arrays.fill(data, length, (int) at, (byte) 0);
		}
		src.get(data, (int) at, n);
		if (at + n > length) {
			length = (int) (at + n);
		}
		dirty = true;
		return n;
	}

	public long size() throws IOException {
		checkOpen();
		return length;
	}

	public long position() throws IOException {
		checkOpen();
		return position;
	}

	public TFileChannel position(long newPosition) throws IOException {
		checkOpen();
		if (newPosition < 0L) {
			throw new IllegalArgumentException("Negative position: " + newPosition);
		}
		position = newPosition;
		return this;
	}

	public TFileChannel truncate(long size) throws IOException {
		checkOpen();
		if (size < 0L) {
			throw new IllegalArgumentException("Negative size: " + size);
		}
		if (!writable) {
			throw new NonWritableChannelException();
		}
		if (size < length) {
			length = (int) size;
			dirty = true;
		}
		if (position > length) {
			position = length;
		}
		return this;
	}

	/** Writes the file back if anything changed. metaData has nothing to mean here. */
	public void force(boolean metaData) throws IOException {
		checkOpen();
		flush();
	}

	public boolean isOpen() {
		return open;
	}

	@Override
	public void close() throws IOException {
		if (!open) {
			return;
		}
		try {
			flush();
		} finally {
			open = false;
			releaseLock();
		}
	}

	/**
	 * Returns null when the path is already locked, which is what DirectoryLock tests for. It
	 * never asks twice on one channel, so an overlapping request is reported the same way
	 * rather than by raising OverlappingFileLockException.
	 */
	public TFileLock tryLock() throws IOException {
		checkOpen();
		if (heldLock != null) {
			return null;
		}
		if (!LOCKED.add(path)) {
			return null;
		}
		heldLock = path;
		return new PageLock();
	}

	public TFileLock tryLock(long at, long size, boolean shared) throws IOException {
		return tryLock();
	}

	/**
	 * The JDK blocks until the lock is free. Nothing could release it while this waits - a
	 * page holds its locks on the same thread that would be blocked - so it fails instead of
	 * hanging.
	 */
	public TFileLock lock() throws IOException {
		TFileLock lock = tryLock();
		if (lock == null) {
			throw new IOException(path + ": already locked in this page");
		}
		return lock;
	}

	public TFileLock lock(long at, long size, boolean shared) throws IOException {
		return lock();
	}

	private void flush() throws IOException {
		if (!dirty) {
			return;
		}
		byte[] out = length == data.length ? data : Arrays.copyOf(data, length);
		new VFile2(path).setAllBytes(out);
		dirty = false;
	}

	private void ensureCapacity(long needed) throws IOException {
		if (needed > Integer.MAX_VALUE) {
			throw new IOException(path + ": file too large for a browser channel: " + needed);
		}
		if (needed <= data.length) {
			return;
		}
		long grown = data.length == 0 ? 8192L : data.length * 2L;
		int capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(grown, needed));
		data = Arrays.copyOf(data, capacity);
	}

	private void releaseLock() {
		if (heldLock != null) {
			LOCKED.remove(heldLock);
			heldLock = null;
		}
	}

	private void checkOpen() throws IOException {
		if (!open) {
			throw new ClosedChannelException();
		}
	}

	private final class PageLock extends TFileLock {
		private boolean valid = true;

		@Override
		public boolean isValid() {
			return valid;
		}

		@Override
		public void release() {
			if (valid) {
				valid = false;
				releaseLock();
			}
		}

		@Override
		public void close() {
			release();
		}
	}
}
