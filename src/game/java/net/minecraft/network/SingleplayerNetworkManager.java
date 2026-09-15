package net.minecraft.network;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lax1dude.eaglercraft.internal.EnumEaglerConnectionState;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.internal.IPCPacketData;
import net.lax1dude.eaglercraft.sp.SingleplayerServerController;
import net.lax1dude.eaglercraft.sp.internal.ClientPlatformSingleplayer;
import net.minecraft.network.protocol.PacketFlow;
import net.lax1dude.eaglercraft.compat.EaglerNetworkManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Copyright (c) 2023-2024 lax1dude, ayunami2000. All Rights Reserved.
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
public class SingleplayerNetworkManager extends EaglerNetworkManager {

	private int debugPacketCounter = 0;
	private byte[][] recievedPacketBuffer = new byte[16384][];
	private int recievedPacketBufferCounter = 0;
	public boolean isPlayerChannelOpen = false;

	public SingleplayerNetworkManager(String channel) {
		super(channel);
		/*
		 * Choose the plugin-message protocol up front, because nothing else will.
		 *
		 * A remote EaglercraftX server announces its version during the WebSocket handshake,
		 * and the client stores it here. There is no such handshake in front of the integrated
		 * server - the connection is two queues in the same page - so the field kept its
		 * initial -1, and NetHandlerSingleplayerLogin.handleGameProfile reads it, asks
		 * GamePluginMessageProtocol.getByVersion(-1), gets null and disconnects the player with
		 * "Unknown protocol selected: -1". Singleplayer could not start at all.
		 *
		 * There is genuinely nothing to negotiate here: both ends of this connection are this
		 * same build, compiled together, so the honest answer is the newest protocol the code
		 * implements. Naming the enum constant rather than a literal means this follows the
		 * protocol if a V5 is ever added.
		 */
		setEaglerProtocolVersion(GamePluginMessageProtocol.V4.ver);
	}

	@Override
	public void connect() {
		clearRecieveQueue();
		SingleplayerServerController.openLocalPlayerChannel();
	}

	@Override
	public EnumEaglerConnectionState getConnectStatus() {
		return isPlayerChannelOpen ? EnumEaglerConnectionState.CONNECTED : EnumEaglerConnectionState.CLOSED;
	}

	@Override
	public void closeChannel(Component reason) {
		SingleplayerServerController.closeLocalPlayerChannel();
		// Leaving a world has to stop the worker, and nothing was doing it. 1.18.2's
		// Minecraft.disconnect() shuts down its own IntegratedServer, which this build does
		// not use - the server lives in a Web Worker - so the worker stayed up with the world
		// still loaded, and SingleplayerServerController was left in WORLD_LOADED. Starting a
		// second world then failed ensureReady()'s WORLD_NONE check: one world per page load.
		// ClientLevel.disconnect() reaches here on "Save and Quit", and the call is a no-op
		// unless a world is actually running, so a failed login does not trip it.
		SingleplayerServerController.shutdownEaglercraftServer();
		if (nethandler != null) {
			nethandler.onDisconnect(reason);
		}
		clearRecieveQueue();
		clientDisconnected = true;
	}

	public void addRecievedPacket(byte[] next) {
		if (recievedPacketBufferCounter < recievedPacketBuffer.length - 1) {
			recievedPacketBuffer[recievedPacketBufferCounter++] = next;
		} else {
			logger.error("Dropping packets on recievedPacketBuffer for channel \"{}\"! (overflow)", address);
		}
	}

	@Override
	public void processReceivedPackets() throws IOException {
		if (nethandler == null)
			return;

		for (int i = 0; i < recievedPacketBufferCounter; ++i) {
			byte[] next = recievedPacketBuffer[i];
			recievedPacketBuffer[i] = null;
			++debugPacketCounter;
			try {
				ByteBuf nettyBuffer = Unpooled.buffer(next, next.length);
				nettyBuffer.writerIndex(next.length);
				FriendlyByteBuf input = new FriendlyByteBuf(nettyBuffer);
				int pktId = input.readVarInt();

				// 1.18.2 merged the 1.12.2 two-step decode: createPacket builds the packet
				// FROM the buffer instead of newInstance() followed by readPacketData.
				Packet<?> pkt;
				try {
					pkt = packetState.createPacket(PacketFlow.CLIENTBOUND, pktId, input);
				} catch (Throwable t) {
					throw new IOException("Failed to read packet type " + pktId + "!", t);
				}

				if (pkt == null) {
					throw new IOException(
							"Recieved packet type " + pktId + " which is undefined in state " + packetState);
				}

				try {
					handlePacket(pkt, nethandler);
				} catch (Throwable t) {
					logger.error("Failed to process {}! It'll be skipped for debug purposes.",
							pkt.getClass().getSimpleName());
					logger.error(t);
					t.printStackTrace();
				}

			} catch (Throwable t) {
				logger.error("Failed to process socket frame {}! It'll be skipped for debug purposes.",
						debugPacketCounter);
				logger.error(t);
			}
		}
		recievedPacketBufferCounter = 0;
	}

	@Override
	public void sendPacket(Packet pkt) {
		if (!isChannelOpen()) {
			logger.error("Packet was sent on a closed connection: {}", pkt.getClass().getSimpleName());
			return;
		}

		int i;
		try {
			i = packetState.getPacketId(PacketFlow.SERVERBOUND, pkt);
		} catch (Throwable t) {
			logger.error("Incorrect packet for state: {} (state is {})", pkt.getClass().getSimpleName(), packetState);
			logger.error(t);
			return;
		}

		temporaryBuffer.clear();
		temporaryBuffer.writeVarInt(i);
		try {
			pkt.write(temporaryBuffer);
		} catch (Throwable ex) { // 1.18.2's Packet.write does not declare IOException
			// The throwable used to be dropped here. A packet that cannot be written is a bug
			// worth a stack trace - without one this reported the packet's name and nothing
			// about why, which is how a buffer fault in the custom-payload path stayed
			// invisible while every symptom pointed somewhere else.
			logger.error("Failed to write packet {}!", pkt.getClass().getSimpleName());
			logger.error(ex);
			return;
		}

		int len = temporaryBuffer.writerIndex();
		byte[] bytes = new byte[len];
		temporaryBuffer.getBytes(0, bytes);

		ClientPlatformSingleplayer.sendPacket(new IPCPacketData(address, bytes));
	}

	@Override
	public boolean checkDisconnected() {
		if (!isPlayerChannelOpen) {
			try {
				processReceivedPackets(); // catch kick message (if any)
			} catch (IOException e) {
			}
			clearRecieveQueue();
			doClientDisconnect(new TranslatableComponent("disconnect.endOfStream"));
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean isLocalChannel() {
		return true;
	}

	public void clearRecieveQueue() {
		for (int i = 0; i < recievedPacketBufferCounter; ++i) {
			recievedPacketBuffer[i] = null;
		}
		recievedPacketBufferCounter = 0;
	}
}
