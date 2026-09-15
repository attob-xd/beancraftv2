package com.mojang.blaze3d.platform;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Replaces blaze3d's PNG header reader, which asked STB for the dimensions of an image
 * without decoding it. There is no STB here, and decoding the whole image through the
 * browser just to learn its size would be wasteful - the answer is in the first 24 bytes.
 *
 * A PNG begins with an 8-byte signature, then the IHDR chunk: a 4-byte length, the tag
 * "IHDR", then width and height as big-endian ints. That is all vanilla wants; it uses it
 * to reject resource-pack icons and skins of the wrong size before loading them.
 */
public class PngInfo {

	private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;

	public final int width;
	public final int height;

	public PngInfo(String name, InputStream input) throws IOException {
		DataInputStream in = new DataInputStream(input);
		if (in.readLong() != PNG_SIGNATURE) {
			throw new IOException("Not a PNG file: " + name);
		}
		in.readInt();                       // IHDR chunk length, always 13
		if (in.readInt() != 0x49484452) {   // "IHDR"
			throw new IOException("First chunk of " + name + " is not IHDR");
		}
		this.width = in.readInt();
		this.height = in.readInt();
	}
}
