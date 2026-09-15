package net.lax1dude.eaglercraft.sp.server.skins;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.lax1dude.eaglercraft.compat.EaglerServerState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.lax1dude.eaglercraft.socket.protocol.pkt.GameMessagePacket;
import net.lax1dude.eaglercraft.socket.protocol.pkt.server.SPacketOtherCapePresetEAG;
import net.lax1dude.eaglercraft.profile.HDCapeData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.lax1dude.eaglercraft.profile.HDCapeChannel;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class IntegratedCapeService {

	public static final Logger logger = LogManager.getLogger("IntegratedCapeService");

	public static final int masterRateLimitPerPlayer = 250;

	private final Map<EaglercraftUUID, GameMessagePacket> capesCache = new HashMap<>();

	// Fork addition: HD capes, and the set of clients that proved they understand
	// them by answering our hdcape:v1 hello.
	private final Map<EaglercraftUUID, HDCapeData> hdCapesCache = new HashMap<>();

	public void processLoginPacket(byte[] packetData, ServerPlayer sender) {
		try {
			IntegratedCapePackets.registerEaglerPlayer(EaglercraftUUID.fromJavaUUID(sender.getUUID()), packetData, this);
		} catch (IOException e) {
			logger.error("Invalid skin data packet recieved from player {}!", sender.getName());
			logger.error(e);
			sender.connection.disconnect(new net.minecraft.network.chat.TextComponent("Invalid skin data packet recieved!"));
		}
	}

	public void registerEaglercraftPlayer(EaglercraftUUID playerUUID, GameMessagePacket capePacket) {
		capesCache.put(playerUUID, capePacket);
	}

	public void processGetOtherCape(EaglercraftUUID searchUUID, ServerPlayer sender) {
		GameMessagePacket maybeCape = capesCache.get(searchUUID);
		if(maybeCape == null) {
			maybeCape = new SPacketOtherCapePresetEAG(searchUUID.msb, searchUUID.lsb, 0);
		}
		EaglerServerState.get(sender.connection).sendEaglerMessage(maybeCape);
	}


	/** Fork addition: an HD cape upload still arriving in chunks. */
	private static final class HDUpload {
		final int offsetX, offsetY, scale, expected;
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		HDUpload(int offsetX, int offsetY, int scale, int expected) {
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			this.scale = scale;
			this.expected = expected;
		}
	}

	private final Map<EaglercraftUUID, HDUpload> hdUploads = new HashMap<>();

	/**
	 * Fork addition: the integrated server's half of the hdcape:v1 channel, so LAN
	 * and singleplayer worlds relay high definition capes the same way the Spigot
	 * plugin does for a real server.
	 */
	public void processHDCapeChannel(ServerPlayer sender, FriendlyByteBuf buf) {
		EaglercraftUUID id = EaglercraftUUID.fromJavaUUID(sender.getUUID());
		try {
			int type = buf.readUnsignedByte();

			if(type == HDCapeChannel.C_CAPE_NONE) {
				hdUploads.remove(id);
				if(hdCapesCache.remove(id) != null) {
					broadcastHDClear(sender, id);
				}
				return;
			}

			if(type == HDCapeChannel.C_CAPE_BEGIN) {
				int offsetX = buf.readShort();
				int offsetY = buf.readShort();
				int scale = buf.readUnsignedShort();
				int expected = buf.readInt();
				if(expected < 0 || expected > HDCapeChannel.MAX_CAPE_IMAGE_BYTES) {
					logger.warn("Rejected oversized HD cape from {} ({} bytes)", sender.getName(), expected);
					hdUploads.remove(id);
					return;
				}
				hdUploads.put(id, new HDUpload(offsetX, offsetY, scale, expected));
				return;
			}

			if(type == HDCapeChannel.C_CAPE_CHUNK) {
				HDUpload upload = hdUploads.get(id);
				if(upload == null) {
					return;
				}
				int remaining = buf.readableBytes();
				// Never trust the sender's declared size.
				if(upload.buffer.size() + remaining > upload.expected) {
					logger.warn("HD cape upload from {} overran its declared size", sender.getName());
					hdUploads.remove(id);
					return;
				}
				byte[] chunk = new byte[remaining];
				buf.readBytes(chunk);
				upload.buffer.write(chunk, 0, chunk.length);

				if(upload.buffer.size() == upload.expected) {
					hdUploads.remove(id);
					HDCapeData cape = new HDCapeData(upload.offsetX, upload.offsetY, upload.scale,
							upload.buffer.toByteArray());
					hdCapesCache.put(id, cape);
					broadcastHDCape(sender, id, cape);
				}
			}
		}catch(Throwable t) {
			hdUploads.remove(id);
			logger.warn("Bad HD cape message from {}: {}", sender.getName(), t.toString());
		}
	}

	/** Invites a joining client to publish its HD cape. */
	public void sendHDHello(ServerPlayer player) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(HDCapeChannel.S_HELLO);
		buf.writeByte(HDCapeChannel.PROTOCOL_VERSION);
		player.connection.send(new ClientboundCustomPayloadPacket(new net.minecraft.resources.ResourceLocation(HDCapeChannel.CHANNEL), buf));
	}

	/** Catches a joining client up on everyone already wearing an HD cape. */
	public void sendKnownHDCapes(ServerPlayer player) {
		EaglercraftUUID self = EaglercraftUUID.fromJavaUUID(player.getUUID());
		for(Map.Entry<EaglercraftUUID, HDCapeData> entry : hdCapesCache.entrySet()) {
			if(!entry.getKey().equals(self)) {
				player.connection.send(new ClientboundCustomPayloadPacket(new net.minecraft.resources.ResourceLocation(HDCapeChannel.CHANNEL),
						encodeHDCape(entry.getKey(), entry.getValue())));
			}
		}
	}

	private FriendlyByteBuf encodeHDCape(EaglercraftUUID id, HDCapeData cape) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeByte(HDCapeChannel.S_OTHER_CAPE);
		buf.writeLong(id.msb);
		buf.writeLong(id.lsb);
		buf.writeShort(cape.offsetX);
		buf.writeShort(cape.offsetY);
		buf.writeShort(cape.scale);
		buf.writeInt(cape.imageData.length);
		buf.writeBytes(cape.imageData);
		return buf;
	}

	private void broadcastHDCape(ServerPlayer from, EaglercraftUUID id, HDCapeData cape) {
		List<ServerPlayer> players = from.server.getPlayerList().getPlayers();
		for(int i = 0; i < players.size(); ++i) {
			ServerPlayer other = players.get(i);
			if(!other.getUUID().equals(from.getUUID())) {
				other.connection.send(
						new ClientboundCustomPayloadPacket(new net.minecraft.resources.ResourceLocation(HDCapeChannel.CHANNEL), encodeHDCape(id, cape)));
			}
		}
	}

	private void broadcastHDClear(ServerPlayer from, EaglercraftUUID id) {
		List<ServerPlayer> players = from.server.getPlayerList().getPlayers();
		for(int i = 0; i < players.size(); ++i) {
			ServerPlayer other = players.get(i);
			if(!other.getUUID().equals(id)) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeByte(HDCapeChannel.S_CLEAR_CAPE);
				buf.writeLong(id.msb);
				buf.writeLong(id.lsb);
				other.connection.send(new ClientboundCustomPayloadPacket(new net.minecraft.resources.ResourceLocation(HDCapeChannel.CHANNEL), buf));
			}
		}
	}

	public void unregisterPlayer(EaglercraftUUID playerUUID) {
		synchronized(capesCache) {
			capesCache.remove(playerUUID);
			hdCapesCache.remove(playerUUID);
		}
	}
}
