package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Missing from TeaVM's class library, and not optional: blaze3d's TextureUtil.readResource
 * wraps every resource stream in Channels.newChannel before copying it into a ByteBuffer,
 * and that is how both shader source and texture data are read. It runs during the first
 * resource reload, so nothing renders without it.
 *
 * The previous version of this file threw from every method, on the reasoning that TeaVM has
 * no channels. That is true of *file* channels - there is no seekable accessor behind a
 * browser filesystem, which is why TFileChannel still refuses - but a channel over a stream
 * needs nothing from the platform. It is a thin adapter, so it is written out here.
 */
public final class TChannels {

	private static final int SCRATCH = 8192;

	private TChannels() {
	}

	public static ReadableByteChannel newChannel(final InputStream in) {
		if (in == null) {
			throw new NullPointerException("in");
		}
		return new ReadableByteChannel() {
			private boolean open = true;
			private final byte[] scratch = new byte[SCRATCH];

			@Override
			public int read(ByteBuffer dst) throws IOException {
				if (!open) {
					throw new ClosedChannelException();
				}
				int room = dst.remaining();
				if (room == 0) {
					return 0;
				}
				int n = in.read(scratch, 0, Math.min(room, scratch.length));
				if (n < 0) {
					return -1;
				}
				dst.put(scratch, 0, n);
				return n;
			}

			@Override
			public boolean isOpen() {
				return open;
			}

			@Override
			public void close() throws IOException {
				open = false;
				in.close();
			}
		};
	}

	public static WritableByteChannel newChannel(final OutputStream out) {
		if (out == null) {
			throw new NullPointerException("out");
		}
		return new WritableByteChannel() {
			private boolean open = true;
			private final byte[] scratch = new byte[SCRATCH];

			@Override
			public int write(ByteBuffer src) throws IOException {
				if (!open) {
					throw new ClosedChannelException();
				}
				int total = src.remaining();
				while (src.hasRemaining()) {
					int n = Math.min(src.remaining(), scratch.length);
					src.get(scratch, 0, n);
					out.write(scratch, 0, n);
				}
				return total;
			}

			@Override
			public boolean isOpen() {
				return open;
			}

			@Override
			public void close() throws IOException {
				open = false;
				out.close();
			}
		};
	}

	public static InputStream newInputStream(final ReadableByteChannel channel) {
		if (channel == null) {
			throw new NullPointerException("channel");
		}
		return new InputStream() {
			private final ByteBuffer one = ByteBuffer.allocate(1);

			@Override
			public int read() throws IOException {
				one.clear();
				int n = channel.read(one);
				return n <= 0 ? -1 : (one.get(0) & 0xFF);
			}

			@Override
			public int read(byte[] dst, int offset, int length) throws IOException {
				if (length == 0) {
					return 0;
				}
				return channel.read(ByteBuffer.wrap(dst, offset, length));
			}

			@Override
			public void close() throws IOException {
				channel.close();
			}
		};
	}

	public static OutputStream newOutputStream(final WritableByteChannel channel) {
		if (channel == null) {
			throw new NullPointerException("channel");
		}
		return new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				channel.write(ByteBuffer.wrap(new byte[] { (byte) b }));
			}

			@Override
			public void write(byte[] src, int offset, int length) throws IOException {
				channel.write(ByteBuffer.wrap(src, offset, length));
			}

			@Override
			public void close() throws IOException {
				channel.close();
			}
		};
	}
}
