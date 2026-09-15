package net.lax1dude.eaglercraft.profile;

/**
 * Fork addition: a high definition cape as it travels over the hdcape:v1 plugin
 * message channel.
 *
 * This used to be an Eaglercraft protocol packet class. It is a plain holder now:
 * HD capes are carried on a normal Minecraft plugin message channel, so there is
 * no reason for this to pretend to be part of the Eaglercraft protocol.
 */
public class HDCapeData {

	public final int offsetX;
	public final int offsetY;
	public final int scale;
	public final byte[] imageData;

	public HDCapeData(int offsetX, int offsetY, int scale, byte[] imageData) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.scale = scale;
		this.imageData = imageData;
	}

}
