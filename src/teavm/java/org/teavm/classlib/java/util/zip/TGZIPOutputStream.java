package org.teavm.classlib.java.util.zip;

import java.io.IOException;
import java.io.OutputStream;

/**
 * TeaVM's class, with one guard added to {@link #flush()}.
 *
 * <p>Upstream's flush is unconditional:
 *
 * <pre>    public void flush() throws IOException {
 *        int count = def.deflate(buf, 0, buf.length, TDeflater.Z_SYNC_FLUSH);
 *        out.write(buf, 0, count);
 *        out.flush();
 *    }</pre>
 *
 * <p>Closing a gzip stream runs {@code finish()} first, which drives the deflater to
 * Z_STREAM_END, and the close chain then flushes on the way out. Asking zlib to deflate again
 * once the stream is complete makes no progress, so it answers Z_BUF_ERROR (-5). That is not a
 * failure in zlib's terms - it means "nothing to do", and the real JDK's Deflater returns 0
 * for it - but TeaVM's {@code TDeflater.deflate} accepts only Z_OK and Z_STREAM_END and turns
 * anything else into {@code RuntimeException("Error: " + err)}.
 *
 * <p>So every gzip stream threw on close. In this game that is the world save:
 * {@code NbtIo.writeCompressed} is how level.dat is written, and the exception surfaced as
 *
 * <pre>    [IntegratedServer/ERROR] Server process encountered a fatal error!
 *    java.lang.RuntimeException: Error: -5
 *        at java.util.zip.Deflater.deflate()
 *        at java.util.zip.GZIPOutputStream.flush()
 *        ...
 *        at net.minecraft.nbt.NbtIo.writeCompressed()</pre>
 *
 * <p>which killed the integrated server worker outright - the world had loaded, and saving it
 * took the server down with it.
 *
 * <p>Skipping the deflate when the deflater has finished is what the JDK does by a different
 * route: its {@code DeflaterOutputStream.flush()} only deflates when the stream was opened in
 * sync-flush mode, which gzip is not by default. {@code out.flush()} still runs either way, so
 * buffered bytes downstream are not stranded.
 *
 * <p>Everything else here is TeaVM's, reproduced so the header and trailer bytes stay
 * identical: the two-byte magic, the deflate method and flag bytes, mtime, XFL and OS in the
 * constructor, and CRC plus ISIZE in {@code finish()}.
 */
public class TGZIPOutputStream extends TDeflaterOutputStream {

	protected TCRC32 crc = new TCRC32();

	public TGZIPOutputStream(OutputStream os) throws IOException {
		this(os, 512);
	}

	public TGZIPOutputStream(OutputStream os, int size) throws IOException {
		super(os, new TDeflater(TDeflater.DEFAULT_COMPRESSION, true), size);
		writeShort(0x8B1F);
		out.write(8); // CM: deflate
		out.write(0); // FLG: no extra fields
		writeLong(0L); // MTIME: not recorded
		out.write(0); // XFL
		out.write(0); // OS
	}

	@Override
	public void flush() throws IOException {
		// The guard. See the class comment - without it, closing the stream asks a finished
		// deflater for more output and TeaVM turns zlib's "no progress" answer into a throw.
		if (!def.finished()) {
			int count = def.deflate(buf, 0, buf.length, TDeflater.Z_SYNC_FLUSH);
			if (count > 0) {
				out.write(buf, 0, count);
			}
		}
		out.flush();
	}

	@Override
	public void finish() throws IOException {
		super.finish();
		writeLong(crc.getValue());
		writeLong(crc.tbytes);
	}

	@Override
	public void write(byte[] buffer, int off, int nbytes) throws IOException {
		super.write(buffer, off, nbytes);
		crc.update(buffer, off, nbytes);
	}

	/** Writes the low four bytes, little-endian - gzip's CRC32 and ISIZE fields are 32 bit. */
	private long writeLong(long i) throws IOException {
		int unsigned = (int) i;
		out.write(unsigned & 0xFF);
		out.write((unsigned >> 8) & 0xFF);
		out.write((unsigned >> 16) & 0xFF);
		out.write((unsigned >> 24) & 0xFF);
		return i;
	}

	private int writeShort(int i) throws IOException {
		out.write(i & 0xFF);
		out.write((i >> 8) & 0xFF);
		return i;
	}
}
