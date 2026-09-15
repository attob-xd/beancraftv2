package net.lax1dude.eaglercraft.socket.protocol.client;

import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.compat.EaglerClientState;
import net.lax1dude.eaglercraft.compat.EaglerOptions;

import net.lax1dude.eaglercraft.profile.SkinModel;
import net.lax1dude.eaglercraft.socket.protocol.pkt.GameMessageHandler;
import net.lax1dude.eaglercraft.socket.protocol.pkt.server.*;
import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EaglercraftUUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

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
public class ClientV3MessageHandler implements GameMessageHandler {

	private final ClientPacketListener netHandler;

	public ClientV3MessageHandler(ClientPacketListener netHandler) {
		this.netHandler = netHandler;
	}

	public void handleServer(SPacketEnableFNAWSkinsEAG packet) {
		EaglerOptions.currentFNAWSkinAllowedState = packet.enableSkins;
		EaglerOptions.currentFNAWSkinForcedState = packet.enableSkins;
		net.lax1dude.eaglercraft.compat.EaglerOptions.enableFNAWSkins = EaglerOptions.currentFNAWSkinForcedState
				|| (EaglerOptions.currentFNAWSkinAllowedState && EaglerOptions.enableFNAWSkins);
	}

	public void handleServer(SPacketOtherCapeCustomEAG packet) {
		EaglerClientState.get(netHandler).getCapeCache().cacheCapeCustom(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast),
				packet.customCape);
	}

	public void handleServer(SPacketOtherCapePresetEAG packet) {
		EaglerClientState.get(netHandler).getCapeCache().cacheCapePreset(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast),
				packet.presetCape);
	}

	public void handleServer(SPacketOtherSkinCustomV3EAG packet) {
		EaglercraftUUID responseUUID = new EaglercraftUUID(packet.uuidMost, packet.uuidLeast);
		SkinModel modelId;
		if(packet.modelID == (byte)0xFF) {
			modelId = EaglerClientState.get(this.netHandler).getSkinCache().getRequestedSkinType(responseUUID);
		}else {
			modelId = SkinModel.getModelFromId(packet.modelID & 0x7F);
			if((packet.modelID & 0x80) != 0 && modelId.sanitize) {
				modelId = SkinModel.STEVE;
			}
		}
		if(modelId.highPoly != null) {
			modelId = SkinModel.STEVE;
		}
		EaglerClientState.get(this.netHandler).getSkinCache().cacheSkinCustom(responseUUID, packet.customSkin, modelId);
	}

	public void handleServer(SPacketOtherSkinPresetEAG packet) {
		EaglerClientState.get(this.netHandler).getSkinCache().cacheSkinPreset(new EaglercraftUUID(packet.uuidMost, packet.uuidLeast),
				packet.presetSkin);
	}

	public void handleServer(SPacketUpdateCertEAG packet) {
	}

	public void handleServer(SPacketVoiceSignalAllowedEAG packet) {
//		if (VoiceClientController.isClientSupported()) {
//			VoiceClientController.handleVoiceSignalPacketTypeAllowed(packet.allowed, packet.iceServers);
//		}
	}

	public void handleServer(SPacketVoiceSignalConnectV3EAG packet) {
//		if (VoiceClientController.isClientSupported()) {
//			if (packet.isAnnounceType) {
//				VoiceClientController.handleVoiceSignalPacketTypeConnectAnnounce(
//						new EaglercraftUUID(packet.uuidMost, packet.uuidLeast));
//			} else {
//				VoiceClientController.handleVoiceSignalPacketTypeConnect(
//						new EaglercraftUUID(packet.uuidMost, packet.uuidLeast), packet.offer);
//			}
//		}
	}

	public void handleServer(SPacketVoiceSignalDescEAG packet) {
//		if (VoiceClientController.isClientSupported()) {
//			VoiceClientController.handleVoiceSignalPacketTypeDescription(
//					new EaglercraftUUID(packet.uuidMost, packet.uuidLeast),
//					new String(packet.desc, StandardCharsets.UTF_8));
//		}
	}

	public void handleServer(SPacketVoiceSignalDisconnectPeerEAG packet) {
//		if (VoiceClientController.isClientSupported()) {
//			VoiceClientController.handleVoiceSignalPacketTypeDisconnect(
//					new EaglercraftUUID(packet.uuidMost, packet.uuidLeast));
//		}
	}

	public void handleServer(SPacketVoiceSignalGlobalEAG packet) {
//		if (VoiceClientController.isClientSupported()) {
//			VoiceClientController.handleVoiceSignalPacketTypeGlobalNew(packet.users);
//		}
	}

	public void handleServer(SPacketVoiceSignalICEEAG packet) {
//		if (VoiceClientController.isClientSupported()) {
//			VoiceClientController.handleVoiceSignalPacketTypeICECandidate(
//					new EaglercraftUUID(packet.uuidMost, packet.uuidLeast),
//					new String(packet.ice, StandardCharsets.UTF_8));
//		}
	}

}
