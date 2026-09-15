package net.lax1dude.eaglercraft.sp.server.socket;

import net.lax1dude.eaglercraft.sp.server.EaglerMinecraftServer;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.network.chat.Component;

/**
 * Copyright (c) 2023-2024 lax1dude. All Rights Reserved.
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
public class NetHandlerHandshakeEagler implements ServerHandshakePacketListener {

	private final EaglerMinecraftServer mcServer;
	private final IntegratedServerPlayerNetworkManager networkManager;

	public NetHandlerHandshakeEagler(EaglerMinecraftServer parMinecraftServer, IntegratedServerPlayerNetworkManager parNetworkManager) {
		this.mcServer = parMinecraftServer;
		this.networkManager = parNetworkManager;
	}

	@Override
	public void onDisconnect(Component var1) {
		
	}

	public void processHandshake(ClientIntentionPacket var1) {
		this.networkManager.setProtocol(var1.getIntention());
		this.networkManager.setListener(new ServerLoginPacketListenerImpl(this.mcServer, this.networkManager));
	}


	/**
	 * 1.12.2 called this {@code processHandshake}. The integrated server only ever sees
	 * LOGIN - nothing pings a singleplayer world - so STATUS is rejected outright.
	 */
	@Override
	public void handleIntention(net.minecraft.network.protocol.handshake.ClientIntentionPacket packet) {
		switch (packet.getIntention()) {
			case LOGIN:
				this.networkManager.setProtocol(net.minecraft.network.ConnectionProtocol.LOGIN);
				this.networkManager.setListener(
						new net.minecraft.server.network.ServerLoginPacketListenerImpl(this.mcServer,
								this.networkManager));
				break;
			default:
				throw new UnsupportedOperationException(
						"Invalid intention " + packet.getIntention() + " on the integrated server");
		}
	}

	/** 1.18.2's PacketListener exposes the connection it is attached to. */
	@Override
	public net.minecraft.network.Connection getConnection() {
		return this.networkManager;
	}
}
