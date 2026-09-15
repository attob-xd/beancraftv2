package org.teavm.classlib.java.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.teavm.classlib.java.nio.channels.TFileChannel;

import net.lax1dude.eaglercraft.internal.vfs2.VFile2;

/**
 * Replaces TeaVM's FileInputStream so that reading a File reads the same filesystem that
 * File reports on - EaglercraftX's VFile2. See TFile for why that move was made.
 *
 * VFile2 hands back the whole file at once rather than a seekable accessor, which suits how
 * it stores data (one IndexedDB record per path) and how vanilla reads these files (options,
 * server list, level.dat - all small, all read start to finish). The bytes are therefore
 * fetched on construction, which is also when a missing file has to be reported: that is
 * what FileNotFoundException is for and what every caller here already handles.
 */
public class TFileInputStream extends InputStream {

	private byte[] data;
	private int position;
	private final String path;

	public TFileInputStream(File file) throws FileNotFoundException {
		this(file == null ? null : file.getPath());
	}

	public TFileInputStream(String path) throws FileNotFoundException {
		if (path == null) {
			throw new NullPointerException("path");
		}
		this.path = path;
		VFile2 vf = new VFile2(path);
		byte[] bytes;
		try {
			bytes = vf.getAllBytes();
		} catch (Throwable t) {
			throw new FileNotFoundException(path + " (" + t + ")");
		}
		if (bytes == null) {
			throw new FileNotFoundException(path);
		}
		this.data = bytes;
	}

	/**
	 * A read-only channel over the same file, positioned where this stream is.
	 *
	 * <p>Not decoration: guava reads a whole file as
	 * {@code ByteStreams.toByteArray(in, in.getChannel().size())}, so every
	 * {@code com.google.common.io.Files.toString} call lands here. Without it the server died
	 * with {@code NoSuchMethodError: java.io.FileInputStream.getChannel()} the moment it loaded
	 * a world that already had {@code advancements/<uuid>.json} - which is to say, any world
	 * being *re-opened* rather than created. Creating a world never touched it, because
	 * PlayerAdvancements.load() skips a file that is not there, so this stayed invisible for as
	 * long as loading an existing world was impossible for other reasons.
	 */
	public TFileChannel getChannel() {
		try {
			TFileChannel channel = TFileChannel.open(Paths.get(path), StandardOpenOption.READ);
			channel.position(position);
			return channel;
		} catch (IOException ex) {
			throw new RuntimeException("Could not open a channel on " + path, ex);
		}
	}

	private void ensureOpen() throws IOException {
		if (data == null) {
			throw new IOException("Stream is closed");
		}
	}

	@Override
	public int read() throws IOException {
		ensureOpen();
		return position >= data.length ? -1 : (data[position++] & 0xFF);
	}

	@Override
	public int read(byte[] dst, int offset, int length) throws IOException {
		ensureOpen();
		if (length == 0) {
			return 0;
		}
		if (position >= data.length) {
			return -1;
		}
		int n = Math.min(length, data.length - position);
		System.arraycopy(data, position, dst, offset, n);
		position += n;
		return n;
	}

	@Override
	public long skip(long count) throws IOException {
		ensureOpen();
		long n = Math.min(count, data.length - position);
		if (n < 0) {
			n = 0;
		}
		position += (int) n;
		return n;
	}

	@Override
	public int available() throws IOException {
		ensureOpen();
		return data.length - position;
	}

	@Override
	public void close() throws IOException {
		data = null;
	}
}
