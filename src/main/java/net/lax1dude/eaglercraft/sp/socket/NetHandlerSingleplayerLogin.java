package net.lax1dude.eaglercraft.sp.socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.lax1dude.eaglercraft.compat.EaglerClientState;

import io.netty.buffer.Unpooled;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageConstants;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

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
public class NetHandlerSingleplayerLogin implements ClientLoginPacketListener {

	private final Minecraft mc;
	private final Screen previousGuiScreen;
	private final net.lax1dude.eaglercraft.compat.EaglerNetworkManager networkManager;

	private static final Logger logger = LogManager.getLogger("NetHandlerSingleplayerLogin");

	public NetHandlerSingleplayerLogin(net.lax1dude.eaglercraft.compat.EaglerNetworkManager parNetworkManager, Minecraft mcIn, Screen parGuiScreen) {
		this.networkManager = parNetworkManager;
		this.mc = mcIn;
		this.previousGuiScreen = parGuiScreen;
	}

	@Override
	public void onDisconnect(Component var1) {
		this.mc.setScreen(new DisconnectedScreen(this.previousGuiScreen, new TranslatableComponent("connect.failed"), var1));
	}

	public void handleEncryptionRequest(ClientboundHelloPacket var1) {
		
	}

	@Override
	public void handleGameProfile(ClientboundGameProfilePacket var1) {
		this.networkManager.setProtocol(ConnectionProtocol.PLAY);
		// 1.12.2 read the plugin-protocol version out of this packet; 1.18.2 carries a real
		// GameProfile, so the version comes off the connection the handshake set it on.
		int p = this.networkManager.getEaglerProtocolVersion();
		GamePluginMessageProtocol mp = GamePluginMessageProtocol.getByVersion(p);
		if(mp == null) {
			this.networkManager.disconnect(new TextComponent("Unknown protocol selected: " + p));
			return;
		}
		logger.info("Server is using protocol: {}", p);
		ClientPacketListener netHandler = new ClientPacketListener(this.mc, this.previousGuiScreen, this.networkManager,
				var1.getGameProfile(), this.mc.createTelemetryManager());
		EaglerClientState.get(netHandler).setEaglerMessageController(
				new GameProtocolMessageController(mp, GamePluginMessageConstants.CLIENT_TO_SERVER,
						GameProtocolMessageController.createClientHandler(p, netHandler),
						// See EaglerPluginChannels: EaglercraftX's channel names are not legal
						// ResourceLocations, so they are mapped rather than wrapped.
						(ch, msg) -> {
							net.minecraft.resources.ResourceLocation chId =
									net.lax1dude.eaglercraft.compat.EaglerPluginChannels.toId(ch);
							if (chId == null) {
								logger.warn("Not sending a message on unknown EaglercraftX channel {}", ch);
								return;
							}
							netHandler.send(new ServerboundCustomPayloadPacket(chId, msg));
						}));
		this.networkManager.setListener(netHandler);
	}

	@Override
	public void handleDisconnect(ClientboundLoginDisconnectPacket var1) {
		networkManager.disconnect(var1.getReason());
	}

	public void handleEnableCompression(ClientboundLoginCompressionPacket var1) {
		
	}


	/**
	 * 1.18.2 added a login-phase custom-query exchange for mod handshakes. Singleplayer
	 * has no such mods, so the query is answered as unhandled.
	 */
	@Override
	public void handleCustomQuery(net.minecraft.network.protocol.login.ClientboundCustomQueryPacket packet) {
		this.networkManager.send(
				new net.minecraft.network.protocol.login.ServerboundCustomQueryPacket(
						packet.getTransactionId(), null));
	}

	/**
	 * The singleplayer channel is in-process, so the server never asks for compression -
	 * setting a threshold here would only cost CPU on both sides of the same page.
	 */
	@Override
	public void handleCompression(net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket packet) {
	}

	@Override
	public net.minecraft.network.Connection getConnection() {
		return this.networkManager;
	}

	/**
	 * The encryption handshake. Singleplayer is in-process, so there is nothing to encrypt
	 * and the server never sends this.
	 */
	@Override
	public void handleHello(net.minecraft.network.protocol.login.ClientboundHelloPacket packet) {
	}
}
