package net.lax1dude.eaglercraft.sp.server;

/**
 * How EaglercraftX names a chunk file in the browser VFS.
 *
 * <p>This is the one piece of the 1.12.2 {@code EaglerChunkPath} still in use: the world
 * converters build chunk filenames with it. The loader itself read and wrote 1.12.2's
 * chunk NBT through {@code IChunkLoader} hooks that 1.18.2 removed along with the chunk
 * format — see src/unported/README.md.
 *
 * <p>The offset makes the coordinate unsigned so the hex encoding is fixed-width and sorts
 * the same way the 1.12.2 fork wrote it; existing worlds keep resolving.
 */
public class EaglerChunkPath {

	private static final String HEX = "0123456789ABCDEF";

	public static String getChunkPath(int x, int z) {
		int unsignedX = x + 1900000;
		int unsignedZ = z + 1900000;

		char[] path = new char[12];
		for (int i = 5; i >= 0; --i) {
			path[i] = HEX.charAt((unsignedX >> (i * 4)) & 0xF);
			path[i + 6] = HEX.charAt((unsignedZ >> (i * 4)) & 0xF);
		}

		return new String(path);
	}

	private EaglerChunkPath() {
	}
}
