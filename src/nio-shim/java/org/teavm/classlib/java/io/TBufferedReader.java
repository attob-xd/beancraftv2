package org.teavm.classlib.java.io;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Replaces TeaVM's BufferedReader, which is a correct buffered reader but has no lines().
 *
 * lines() is on the boot path twice. Options.load() reads options.txt through
 * guava's Files.newReader(...).lines(), inside Minecraft's constructor, and SplashManager
 * reads splashes.txt the same way during the first resource reload. Neither is optional, and
 * TeaVM has no partial-class mechanism, so adding one method means supplying the class.
 *
 * The buffering below is an ordinary reimplementation - fill a char[] from the underlying
 * reader, hand characters out of it, refill when empty - and readLine() accepts CR, LF and
 * CRLF as line endings, as the real one does.
 *
 * lines() is eager: it reads the whole reader into a list and streams that. The real one is
 * lazy, which matters for a file too large to hold. Nothing here reads such a file - the two
 * callers are a settings file and a list of splash texts - and a lazy Stream would need a
 * Spliterator that re-enters readLine() from inside stream traversal, which is more machinery
 * than the use justifies. The visible difference is that an IOException surfaces from
 * lines() itself rather than from the first terminal operation, wrapped as
 * UncheckedIOException either way.
 */
public class TBufferedReader extends Reader {

	private static final int DEFAULT_SIZE = 8192;

	private Reader in;
	private final char[] buffer;
	private int position;
	private int limit;
	private boolean skipLfAfterCr;

	public TBufferedReader(Reader in) {
		this(in, DEFAULT_SIZE);
	}

	public TBufferedReader(Reader in, int size) {
		if (size <= 0) {
			throw new IllegalArgumentException("Buffer size must be positive: " + size);
		}
		this.in = in;
		this.buffer = new char[size];
	}

	private void requireOpen() throws IOException {
		if (in == null) {
			throw new IOException("Reader is closed");
		}
	}

	/** @return false at end of input. */
	private boolean fill() throws IOException {
		if (position < limit) {
			return true;
		}
		position = 0;
		limit = in.read(buffer, 0, buffer.length);
		if (limit <= 0) {
			limit = 0;
			return false;
		}
		return true;
	}

	@Override
	public int read() throws IOException {
		requireOpen();
		if (!fill()) {
			return -1;
		}
		return buffer[position++];
	}

	@Override
	public int read(char[] dst, int offset, int length) throws IOException {
		requireOpen();
		if (length == 0) {
			return 0;
		}
		if (!fill()) {
			return -1;
		}
		int n = Math.min(length, limit - position);
		System.arraycopy(buffer, position, dst, offset, n);
		position += n;
		return n;
	}

	public String readLine() throws IOException {
		requireOpen();
		StringBuilder sb = null;
		while (true) {
			if (!fill()) {
				return sb != null && sb.length() > 0 ? sb.toString() : null;
			}
			if (skipLfAfterCr) {
				skipLfAfterCr = false;
				if (buffer[position] == '\n') {
					++position;
					continue;
				}
			}
			for (int i = position; i < limit; ++i) {
				char c = buffer[i];
				if (c == '\n' || c == '\r') {
					if (sb == null) {
						sb = new StringBuilder();
					}
					sb.append(buffer, position, i - position);
					position = i + 1;
					// A CR may be followed by an LF that belongs to the same break, and the
					// LF can be the first character of the next buffer fill.
					if (c == '\r') {
						if (position < limit) {
							if (buffer[position] == '\n') {
								++position;
							}
						} else {
							skipLfAfterCr = true;
						}
					}
					return sb.toString();
				}
			}
			if (sb == null) {
				sb = new StringBuilder();
			}
			sb.append(buffer, position, limit - position);
			position = limit;
		}
	}

	/** Eager; see the class comment. */
	public Stream<String> lines() {
		List<String> out = new ArrayList<>();
		try {
			String line;
			while ((line = readLine()) != null) {
				out.add(line);
			}
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		return out.stream();
	}

	@Override
	public boolean ready() throws IOException {
		requireOpen();
		return position < limit || in.ready();
	}

	@Override
	public long skip(long count) throws IOException {
		requireOpen();
		long remaining = count;
		while (remaining > 0) {
			if (!fill()) {
				break;
			}
			int n = (int) Math.min(remaining, limit - position);
			position += n;
			remaining -= n;
		}
		return count - remaining;
	}

	/**
	 * Marking is not supported. TeaVM's version supports it within the buffer; nothing in
	 * this build marks a reader, and claiming support that silently fails past the buffer
	 * would be worse than saying so.
	 */
	@Override
	public boolean markSupported() {
		return false;
	}

	@Override
	public void mark(int readAheadLimit) throws IOException {
		throw new IOException("mark is not supported");
	}

	@Override
	public void reset() throws IOException {
		throw new IOException("mark is not supported");
	}

	@Override
	public void close() throws IOException {
		if (in != null) {
			try {
				in.close();
			} finally {
				in = null;
			}
		}
	}
}
