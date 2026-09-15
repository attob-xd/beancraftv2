package net.lax1dude.eaglercraft.sp.server.socket.protocol;

import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.lax1dude.eaglercraft.socket.protocol.pkt.GameMessageHandler;
import net.lax1dude.eaglercraft.socket.protocol.pkt.client.*;
import net.lax1dude.eaglercraft.socket.protocol.pkt.server.SPacketOtherPlayerClientUUIDV4EAG;
import net.lax1dude.eaglercraft.sp.server.EaglerMinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.lax1dude.eaglercraft.compat.EaglerServerState;

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
public class ServerV4MessageHandler implements GameMessageHandler {

	private final ServerGamePacketListenerImpl netHandler;
	private final EaglerMinecraftServer server;

	public ServerV4MessageHandler(ServerGamePacketListenerImpl netHandler) {
		this.netHandler = netHandler;
		this.server = (EaglerMinecraftServer)net.lax1dude.eaglercraft.compat.EaglerServerState.get(netHandler).serverController;
	}

	public void handleClient(CPacketGetOtherCapeEAG packet) {
		server.getCapeService().processGetOtherCape(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), netHandler.player);
	}

	public void handleClient(CPacketGetOtherSkinEAG packet) {
		server.getSkinService().processPacketGetOtherSkin(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), netHandler.player);
	}

	public void handleClient(CPacketGetSkinByURLEAG packet) {
		server.getSkinService().processPacketGetOtherSkin(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), packet.url, netHandler.player);
	}

	public void handleClient(CPacketInstallSkinSPEAG packet) {
		server.getSkinService().processPacketInstallNewSkin(packet.customSkin, netHandler.player);
	}

	public void handleClient(CPacketVoiceSignalConnectEAG packet) {
//		IntegratedVoiceService voiceSvc = server.getVoiceService();
//		if(voiceSvc != null) {
//			voiceSvc.handleVoiceSignalPacketTypeConnect(netHandler.player);
//		}
	}

	public void handleClient(CPacketVoiceSignalDescEAG packet) {
//		IntegratedVoiceService voiceSvc = server.getVoiceService();
//		if(voiceSvc != null) {
//			voiceSvc.handleVoiceSignalPacketTypeDesc(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), packet.desc, netHandler.player);
//		}
	}

	public void handleClient(CPacketVoiceSignalDisconnectV4EAG packet) {
//		IntegratedVoiceService voiceSvc = server.getVoiceService();
//		if(voiceSvc != null) {
//			voiceSvc.handleVoiceSignalPacketTypeDisconnect(netHandler.player);
//		}
	}

	public void handleClient(CPacketVoiceSignalDisconnectPeerV4EAG packet) {
//		IntegratedVoiceService voiceSvc = server.getVoiceService();
//		if(voiceSvc != null) {
//			voiceSvc.handleVoiceSignalPacketTypeDisconnectPeer(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), netHandler.player);
//		}
	}

	public void handleClient(CPacketVoiceSignalICEEAG packet) {
//		IntegratedVoiceService voiceSvc = server.getVoiceService();
//		if(voiceSvc != null) {
//			voiceSvc.handleVoiceSignalPacketTypeICE(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), packet.ice, netHandler.player);
//		}
	}

	public void handleClient(CPacketVoiceSignalRequestEAG packet) {
//		IntegratedVoiceService voiceSvc = server.getVoiceService();
//		if(voiceSvc != null) {
//			voiceSvc.handleVoiceSignalPacketTypeRequest(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), netHandler.player);
//		}
	}

	public void handleClient(CPacketGetOtherClientUUIDV4EAG packet) {
		ServerPlayer player = this.server.getPlayerList().getPlayer(new java.util.UUID(packet.playerUUIDMost, packet.playerUUIDLeast));
		if(player != null && net.lax1dude.eaglercraft.compat.EaglerServerState.getClientBrandUUID(player) != null) {
			EaglerServerState.get(netHandler).sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(packet.requestId, net.lax1dude.eaglercraft.compat.EaglerServerState.getClientBrandUUID(player).getMostSignificantBits(), net.lax1dude.eaglercraft.compat.EaglerServerState.getClientBrandUUID(player).getLeastSignificantBits()));
		}else {
			EaglerServerState.get(netHandler).sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(packet.requestId, 0l, 0l));
		}
	}

}
