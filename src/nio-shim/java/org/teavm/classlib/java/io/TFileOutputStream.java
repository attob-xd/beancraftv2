package org.teavm.classlib.java.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import net.lax1dude.eaglercraft.internal.vfs2.VFile2;

/**
 * Replaces TeaVM's FileOutputStream so that writing a File writes the filesystem File
 * reports on - EaglercraftX's VFile2. See TFile.
 *
 * VFile2 stores a whole file in one write, so the bytes are buffered here and committed on
 * close(). Two consequences worth stating: a stream that is never closed writes nothing, and
 * flush() cannot push data through early because there is nothing to push it into. Vanilla
 * closes these streams (Options.save, NbtIo, the level storage) so this holds in practice,
 * and it is the same shape as the rest of EaglercraftX's own file writing.
 *
 * Append opens with the existing contents already in the buffer, so appending to a missing
 * file starts empty rather than failing - as it does on a real filesystem.
 */
public class TFileOutputStream extends OutputStream {

	private final VFile2 target;
	private ByteArrayOutputStream buffer;

	public TFileOutputStream(File file) throws FileNotFoundException {
		this(file == null ? null : file.getPath(), false);
	}

	public TFileOutputStream(File file, boolean append) throws FileNotFoundException {
		this(file == null ? null : file.getPath(), append);
	}

	public TFileOutputStream(String path) throws FileNotFoundException {
		this(path, false);
	}

	public TFileOutputStream(String path, boolean append) throws FileNotFoundException {
		if (path == null) {
			throw new NullPointerException("path");
		}
		this.target = new VFile2(path);
		this.buffer = new ByteArrayOutputStream();
		if (append) {
			byte[] existing;
			try {
				existing = target.getAllBytes();
			} catch (Throwable t) {
				existing = null;
			}
			if (existing != null) {
				buffer.write(existing, 0, existing.length);
			}
		}
	}

	private void ensureOpen() throws IOException {
		if (buffer == null) {
			throw new IOException("Stream is closed");
		}
	}

	@Override
	public void write(int b) throws IOException {
		ensureOpen();
		buffer.write(b);
	}

	@Override
	public void write(byte[] src, int offset, int length) throws IOException {
		ensureOpen();
		buffer.write(src, offset, length);
	}

	/** Nothing to flush to; the file is written whole on close. See the class comment. */
	@Override
	public void flush() throws IOException {
		ensureOpen();
	}

	@Override
	public void close() throws IOException {
		if (buffer == null) {
			return;
		}
		byte[] bytes = buffer.toByteArray();
		buffer = null;
		try {
			target.setAllBytes(bytes);
		} catch (Throwable t) {
			throw new IOException("Could not write " + target.getPath(), t);
		}
	}
}
