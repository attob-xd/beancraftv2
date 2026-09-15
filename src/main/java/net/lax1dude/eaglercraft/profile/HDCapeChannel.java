package net.lax1dude.eaglercraft.profile;

/**
 * Fork addition: wire format for HD capes over a plain Minecraft plugin message
 * channel.
 *
 * This deliberately does NOT extend the Eaglercraft protocol. That protocol
 * throws on unknown packet IDs, so any server that had not been patched would
 * drop the player. Unknown plugin message CHANNELS, by contrast, are silently
 * ignored by every server and client, so this transport is safe to speak
 * anywhere and a normal Bukkit / BungeeCord / Velocity plugin can carry it.
 *
 * Note the asymmetric size limits in vanilla: a client to server custom payload
 * caps at 32767 bytes while server to client allows 1048576. The upload is
 * therefore chunked, the download is not.
 */
public class HDCapeChannel {

	public static final String CHANNEL = "hdcape:v1";

	public static final int PROTOCOL_VERSION = 1;

	// client -> server
	public static final int C_CAPE_BEGIN = 0x01;
	public static final int C_CAPE_CHUNK = 0x02;
	public static final int C_CAPE_NONE = 0x03;

	// server -> client
	public static final int S_HELLO = 0x10;
	public static final int S_OTHER_CAPE = 0x11;
	public static final int S_CLEAR_CAPE = 0x12;

	/** Chunk payload size, well under the 32767 byte client to server ceiling. */
	public static final int MAX_CHUNK_PAYLOAD = 30000;

	/** Matches the cap enforced in the cape editor. */
	public static final int MAX_CAPE_IMAGE_BYTES = 262144;

}
