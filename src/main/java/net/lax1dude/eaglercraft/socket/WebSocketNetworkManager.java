package net.lax1dude.eaglercraft.socket;

import java.io.IOException;
import java.util.List;
import net.lax1dude.eaglercraft.compat.EaglerNetworkManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.lax1dude.eaglercraft.internal.EnumEaglerConnectionState;
import net.lax1dude.eaglercraft.internal.IWebSocketClient;
import net.lax1dude.eaglercraft.internal.IWebSocketFrame;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * Copyright (c) 2022-2024 lax1dude. All Rights Reserved.
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
public class WebSocketNetworkManager extends EaglerNetworkManager {

	protected final IWebSocketClient webSocketClient;

	public WebSocketNetworkManager(IWebSocketClient webSocketClient) {
		super(webSocketClient.getCurrentURI());
		this.webSocketClient = webSocketClient;
	}

	public void connect() {
	}

	public EnumEaglerConnectionState getConnectStatus() {
		return webSocketClient.getState();
	}

	public void closeChannel(Component reason) {
		webSocketClient.close();
		if(nethandler != null) {
			nethandler.onDisconnect(reason);
		}
		clientDisconnected = true;
	}

	public void processReceivedPackets() throws IOException {
		if(nethandler == null) return;
		if(webSocketClient.availableStringFrames() > 0) {
			logger.warn("discarding {} string frames recieved on a binary connection", webSocketClient.availableStringFrames());
			webSocketClient.clearStringFrames();
		}
		List<IWebSocketFrame> pkts = webSocketClient.getNextBinaryFrames();

		if(pkts == null) {
			return;
		}

		for(int i = 0, l = pkts.size(); i < l; ++i) {
			IWebSocketFrame next = pkts.get(i);
			++debugPacketCounter;
			try {
				byte[] asByteArray = next.getByteArray();
				ByteBuf nettyBuffer = Unpooled.buffer(asByteArray, asByteArray.length);
				nettyBuffer.writerIndex(asByteArray.length);
				FriendlyByteBuf input = new FriendlyByteBuf(nettyBuffer);
				int pktId = input.readVarInt();

				// 1.18.2 merged the two 1.12.2 steps: packets no longer have a no-arg
				// constructor plus readPacketData - createPacket builds the packet FROM
				// the buffer, so a decode failure surfaces here rather than separately.
				Packet<?> pkt;
				try {
					pkt = packetState.createPacket(PacketFlow.CLIENTBOUND, pktId, input);
				}catch(Throwable t) {
					throw new IOException("Failed to read packet type " + pktId + "!", t);
				}

				if(pkt == null) {
					throw new IOException("Recieved packet type " + pktId + " which is undefined in state " + packetState);
				}
				
				try {
					handlePacket(pkt, nethandler);
				}catch(Throwable t) {
					logger.error("Failed to process {}! It'll be skipped for debug purposes.", pkt.getClass().getSimpleName());
					logger.error(t);
				}
				
			}catch(Throwable t) {
				logger.error("Failed to process websocket frame {}! It'll be skipped for debug purposes.", debugPacketCounter);
				logger.error(t);
			}
		}
	}

	public void sendPacket(Packet pkt) {
		if(!isChannelOpen()) {
			logger.error("Packet was sent on a closed connection: {}", pkt.getClass().getSimpleName());
			return;
		}
		
		int i;
		try {
			i = packetState.getPacketId(PacketFlow.SERVERBOUND, pkt);
		}catch(Throwable t) {
			logger.error("Incorrect packet for state: {} (state is {})", pkt.getClass().getSimpleName(), packetState);
			logger.error(t);
			return;
		}
		
		temporaryBuffer.clear();
		temporaryBuffer.writeVarInt(i);
		try {
			// 1.12.2's writePacketData threw IOException; 1.18.2's write() does not, but a
			// malformed packet can still throw at runtime, so the guard stays.
			pkt.write(temporaryBuffer);
		}catch(Throwable ex) {
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
		
		webSocketClient.send(bytes);
	}

	public boolean checkDisconnected() {
		if(webSocketClient.isClosed()) {
			try {
				processReceivedPackets(); // catch kick message
			} catch (IOException e) {
			}
			doClientDisconnect(new TranslatableComponent("disconnect.endOfStream"));
			return true;
		}else {
			return false;
		}
	}

}
