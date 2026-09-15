package io.netty.buffer;

import java.nio.ByteBuffer;

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
public class Unpooled {

	public static final ByteBuf EMPTY_BUFFER = ByteBuf.allocate(0, 0);

	public static ByteBuf buffer() {
		return ByteBuf.allocate(256, Integer.MAX_VALUE);
	}

	public static ByteBuf buffer(int length) {
		return ByteBuf.allocate(length, Integer.MAX_VALUE);
	}

	public static ByteBuf buffer(int length, int maxLength) {
		return ByteBuf.allocate(length, maxLength);
	}

	public static ByteBuf buffer(ByteBuffer data, int maxLength) {
		return ByteBuf.allocate(data, maxLength);
	}

	public static ByteBuf buffer(byte[] data, int maxLength) {
		return ByteBuf.allocate(ByteBuffer.wrap(data), maxLength);
	}

	public static ByteBuf wrappedBuffer(ByteBuf buf) {
		return buf.duplicate();
	}

	/**
	 * Returns a buffer whose readable region is the whole array, which is netty's contract:
	 * "wrapped" means the bytes are already content, so readerIndex is 0 and writerIndex is
	 * the length.
	 *
	 * <p>This returned a buffer with writerIndex 0 instead - the bytes were reachable by
	 * capacity but netty considered the buffer empty, so the first read off it threw
	 * {@code readerIndex(0) + length(2) exceeds writerIndex(0)}. Vanilla wraps a byte[] to
	 * re-read a payload it has already received, most visibly in
	 * {@code ClientboundLevelChunkPacketData.getReadBuffer}, so every chunk the server sent
	 * failed to load and the world stayed empty.
	 */
	public static ByteBuf wrappedBuffer(byte[] array) {
		int size = array.length;
		if (size == 0) {
			return EMPTY_BUFFER;
		}
		ByteBuf buf = buffer(array, size);
		buf.writerIndex(size);
		return buf;
	}
}