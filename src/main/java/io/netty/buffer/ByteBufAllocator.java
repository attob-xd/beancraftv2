package io.netty.buffer;

/**
 * EaglercraftX vendors netty's buffer package rather than shipping the real one, and never
 * needed an allocator: Unpooled hands out buffers backed by a plain array. 1.18.2's
 * FriendlyByteBuf names the interface in its metadata, though, which TeaVM evaluates at
 * load time - so it has to exist.
 *
 * Only the two methods vanilla calls are declared, and both go to Unpooled, which is where
 * every buffer in this build comes from anyway.
 */
public interface ByteBufAllocator {

	ByteBufAllocator DEFAULT = new ByteBufAllocator() {
		@Override
		public ByteBuf buffer() {
			return Unpooled.buffer();
		}

		@Override
		public ByteBuf buffer(int initialCapacity) {
			return Unpooled.buffer(initialCapacity);
		}

		@Override
		public ByteBuf heapBuffer() {
			return Unpooled.buffer();
		}

		@Override
		public ByteBuf heapBuffer(int initialCapacity) {
			return Unpooled.buffer(initialCapacity);
		}
	};

	ByteBuf buffer();

	ByteBuf buffer(int initialCapacity);

	ByteBuf heapBuffer();

	ByteBuf heapBuffer(int initialCapacity);
}
