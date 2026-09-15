package io.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Copyright (c) 2022 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class ByteBufEaglercraftImpl extends AbstractByteBuf {

	private ByteBuffer internal;

	public ByteBufEaglercraftImpl(ByteBuffer internal, int maxCapacity) {
		super(maxCapacity);
		if (internal.order() != ByteOrder.BIG_ENDIAN) {
			this.internal = internal.order(ByteOrder.BIG_ENDIAN);
		} else {
			this.internal = internal;
		}
	}

	@Override
	protected byte _getByte(int index) {
		return internal.get(index);
	}

	@Override
	protected short _getShort(int index) {
		return internal.getShort(index);
	}

	@Override
	protected int _getUnsignedMedium(int index) {
		return ((internal.get(index) & 0xFF) << 16) | ((internal.get(index + 1) & 0xFF) << 8)
				| (internal.get(index + 2) & 0xFF);
	}

	@Override
	protected int _getInt(int index) {
		return internal.getInt(index);
	}

	@Override
	protected long _getLong(int index) {
		return internal.getLong(index);
	}

	@Override
	protected void _setByte(int index, int value) {
		internal.put(index, (byte) value);
	}

	@Override
	protected void _setShort(int index, int value) {
		internal.putShort(index, (short) value);
	}

	@Override
	protected void _setMedium(int index, int value) {
		internal.put(index, (byte) ((value >>> 16) & 0xFF));
		internal.put(index + 1, (byte) ((value >>> 8) & 0xFF));
		internal.put(index + 2, (byte) (value & 0xFF));
	}

	@Override
	protected void _setInt(int index, int value) {
		internal.putInt(index, value);
	}

	@Override
	protected void _setLong(int index, long value) {
		internal.putLong(index, value);
	}

	@Override
	public int capacity() {
		return internal.capacity();
	}

	@Override
	public ByteBuf capacity(int newCapacity) {
		if (newCapacity > internal.capacity()) {
			ByteBuffer newCap = ByteBuffer.wrap(new byte[(int) (newCapacity * 1.5f)]);
			NioBufferFunctions.put(newCap, 0, internal, 0, internal.capacity());
			newCap.clear();
			internal = newCap;
		}
		return this;
	}

	@Override
	public ByteOrder order() {
		return ByteOrder.BIG_ENDIAN;
	}

	@Override
	public ByteBuf order(ByteOrder endianness) {
		throw new UnsupportedOperationException("Not supported as it is not used by Eaglercraft");
	}

	@Override
	public ByteBuf unwrap() {
		return this;
	}

	@Override
	public boolean isDirect() {
		return false;
	}

	/**
	 * Copies into any {@link ByteBuf}, not only this implementation.
	 *
	 * <p>This used to reject anything else outright with "The buffer passed is not an
	 * Eaglercraft byte buffer!". netty's contract puts no such restriction on the argument, and
	 * Minecraft passes a {@code FriendlyByteBuf} - a wrapper around a buffer, not this class -
	 * all over the packet code. The visible failure was that the client could not send its
	 * brand: {@code ServerboundCustomPayloadPacket.write} calls {@code writeBytes(ByteBuf)},
	 * which lands here, so {@code handleLogin} threw partway through on every join.
	 *
	 * <p>The fast path is kept for the common case where both sides really are this class; the
	 * fallback reads through the public ByteBuf API, which every implementation supports.
	 */
	@Override
	public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
		if (dst instanceof ByteBufEaglercraftImpl) {
			NioBufferFunctions.put(((ByteBufEaglercraftImpl) dst).internal, dstIndex, internal, index, length);
		} else {
			for (int i = 0; i < length; ++i) {
				dst.setByte(dstIndex + i, internal.get(index + i));
			}
		}
		return this;
	}

	@Override
	public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
		NioBufferFunctions.get(internal, index, dst, dstIndex, length);
		return this;
	}

	@Override
	public ByteBuf getBytes(int index, ByteBuffer dst) {
		NioBufferFunctions.put(dst, dst.position(), internal, index, dst.remaining());
		dst.position(dst.limit());
		return this;
	}

	@Override
	public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
		byte[] buf = new byte[length];
		NioBufferFunctions.get(internal, index, buf);
		out.write(buf);
		return this;
	}

	/** See {@link #getBytes(int, ByteBuf, int, int)} - same restriction, same reason to drop it. */
	@Override
	public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
		if (src instanceof ByteBufEaglercraftImpl) {
			NioBufferFunctions.put(internal, index, ((ByteBufEaglercraftImpl) src).internal, srcIndex, length);
		} else {
			for (int i = 0; i < length; ++i) {
				internal.put(index + i, src.getByte(srcIndex + i));
			}
		}
		return this;
	}

	@Override
	public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
		NioBufferFunctions.put(internal, index, src, srcIndex, length);
		return this;
	}

	@Override
	public ByteBuf setBytes(int index, ByteBuffer src) {
		NioBufferFunctions.put(internal, index, src, src.position(), src.remaining());
		src.position(src.limit());
		return this;
	}

	@Override
	public int setBytes(int index, InputStream in, int length) throws IOException {
		byte[] buf = new byte[length];
		int r = in.read(buf, 0, length);
		if (r > 0) {
			NioBufferFunctions.put(internal, index, buf, 0, r);
		}
		return r;
	}

	/**
	 * The third of the trio, and it had the same hole {@link #slice} and {@link #duplicate}
	 * did: the copy is the readable content, so it has to arrive readable. A fresh buffer
	 * starts with both indices at 0, so the copy came back with the right bytes and a writer
	 * index of zero - present by capacity, invisible to every read.
	 *
	 * <p>{@code ClientboundCustomPayloadPacket.getData()} is {@code new FriendlyByteBuf(
	 * this.data.copy())}, so this is the buffer every plugin message is read from. The
	 * server's brand failed here on every join - "readerIndex(0) + length(1) exceeds
	 * writerIndex(0): ByteBufEaglercraftImpl(ridx: 0, widx: 0, cap: 17/34)", seventeen bytes
	 * of "Paper (Velocity)" that nothing could see - and so did every EaglercraftX skin, cape
	 * and voice message, which read through the same getData().
	 */
	@Override
	public ByteBuf copy(int index, int length) {
		byte[] cpy = new byte[length];
		NioBufferFunctions.get(internal, index, cpy);
		ByteBufEaglercraftImpl copied = new ByteBufEaglercraftImpl(ByteBuffer.wrap(cpy), maxCapacity());
		copied.setIndex(0, length);
		return copied;
	}

	@Override
	public int nioBufferCount() {
		return 1;
	}

	/**
	 * The commented-out body was {@code internal.slice(index, length)}, which is the two-arg
	 * {@code ByteBuffer.slice} added in Java 13 - hence the "JDK 8" message. Throwing instead
	 * was not a safe placeholder: netty routes {@code ByteBuf.toString(int, int, Charset)}
	 * through {@code ByteBufUtil.decodeString}, which calls this. That is how every string on
	 * the wire is read, so {@code FriendlyByteBuf.readUtf} threw on the first packet of a
	 * multiplayer join - the join itself succeeded, then every packet carrying a string or a
	 * resource location was skipped, and the connection sat there until the handshake timed
	 * out.
	 *
	 * <p>Duplicating and slicing reaches the same result with Java 8 API only. The duplicate
	 * is what keeps this safe to call mid-read: it carries its own position and limit, so
	 * narrowing them here cannot disturb the reader index of the buffer being parsed, which is
	 * exactly what {@link #internalNioBuffer} below does do (deliberately - netty documents
	 * that one as destructive).
	 */
	@Override
	public ByteBuffer nioBuffer(int index, int length) {
		ByteBuffer dup = internal.duplicate();
		dup.position(index);
		dup.limit(index + length);
		return dup.slice();
	}

	@Override
	public ByteBuffer internalNioBuffer(int index, int length) {
		internal.position(index).limit(index + length);
		return internal;
	}

	@Override
	public ByteBuffer[] nioBuffers(int index, int length) {
		// nioBufferCount() is 1, so the array netty expects is always a single component.
		return new ByteBuffer[] { nioBuffer(index, length) };
	}

	@Override
	public boolean hasArray() {
		return true;
	}

	@Override
	public byte[] array() {
		return internal.array();
	}

	@Override
	public int arrayOffset() {
		// Was hardcoded to 0, which held only because every buffer here came from
		// ByteBuffer.wrap. slice() below produces buffers whose array starts partway into the
		// backing array, and a caller pairing array() with a wrong offset reads the wrong
		// bytes rather than failing.
		return internal.arrayOffset();
	}

	@Override
	public boolean hasMemoryAddress() {
		return false;
	}

	@Override
	public long memoryAddress() {
		return 0;
	}

	/**
	 * Shares memory with this buffer, as netty's contract requires - {@code slice} is a view,
	 * not a copy, and {@link #copy} above is the copying one. The duplicate only gives the
	 * slice independent position and limit; the bytes underneath are the same array.
	 */
	@Override
	public ByteBuf slice(int index, int length) {
		ByteBufEaglercraftImpl sliced = new ByteBufEaglercraftImpl(nioBuffer(index, length), maxCapacity());
		// A slice is entirely readable content, so its writer index is the length - netty's
		// slice() hands back a buffer you can read straight away. A fresh buffer starts with
		// both indices at 0, and leaving it that way made every slice look empty: the bytes
		// were present by capacity and invisible to every read.
		//
		// This was first blamed for the server brand failing to decode. It was not: the
		// packet takes its payload with readBytes(), and getData() hands it out through
		// copy(). Same bug, different method - see copy(int, int). The slice fix is right on
		// its own terms and stays, but it fixed a different set of callers than the one that
		// prompted it.
		sliced.setIndex(0, length);
		return sliced;
	}

	/**
	 * Decodes straight out of the backing buffer instead of going through netty's
	 * {@code ByteBufUtil.decodeString}, which builds a CharsetDecoder and drives it over a
	 * temporary nio buffer. This is the same absolute-index style as NioBufferFunctions, it
	 * avoids a decoder TeaVM has no reason to exercise, and it is the hot path for every
	 * string read off the network.
	 */
	@Override
	public String toString(int index, int length, Charset charset) {
		if (length == 0) {
			return "";
		}
		byte[] tmp = new byte[length];
		NioBufferFunctions.get(internal, index, tmp);
		return new String(tmp, charset);
	}

	/**
	 * Netty's duplicate shares content and starts with the same reader and writer indices;
	 * only the marks are left behind. This built the new buffer and left both indices at 0, so
	 * a duplicate of a full buffer read as an empty one - which is how
	 * {@code Unpooled.wrappedBuffer(ByteBuf)} silently produced unreadable buffers.
	 */
	@Override
	public ByteBuf duplicate() {
		ByteBufEaglercraftImpl dup = new ByteBufEaglercraftImpl(internal.duplicate(), maxCapacity());
		dup.setIndex(readerIndex(), writerIndex());
		return dup;
	}

}
