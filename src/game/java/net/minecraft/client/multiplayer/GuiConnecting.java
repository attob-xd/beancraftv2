package net.minecraft.client.multiplayer;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.cookie.ServerCookieDataStore;
import net.lax1dude.eaglercraft.internal.EnumEaglerConnectionState;
import net.lax1dude.eaglercraft.internal.IWebSocketClient;
import net.lax1dude.eaglercraft.internal.IWebSocketFrame;
import net.lax1dude.eaglercraft.internal.PlatformNetworking;
import net.lax1dude.eaglercraft.socket.AddressResolver;
import net.lax1dude.eaglercraft.socket.ConnectionHandshake;
import net.lax1dude.eaglercraft.socket.RateLimitTracker;
import net.lax1dude.eaglercraft.socket.WebSocketNetworkManager;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageConstants;
import net.lax1dude.eaglercraft.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.socket.protocol.client.GameProtocolMessageController;
import net.minecraft.client.Minecraft;
import net.lax1dude.eaglercraft.compat.GuiButtonCompat;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.lax1dude.eaglercraft.compat.GuiScreenCompat;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.ConnectionProtocol;
import net.lax1dude.eaglercraft.compat.EaglerNetworkManager;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.network.chat.TextComponent;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files (c) 2022-2025 lax1dude, ayunami2000. All Rights Reserved.
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
public class GuiConnecting extends GuiScreenCompat {
	private static final Logger logger = LogManager.getLogger();
	private IWebSocketClient webSocket;
	private EaglerNetworkManager networkManager;
	private String currentAddress;
	private String currentPassword;
	private boolean allowPlaintext;
	private boolean allowCookies;
	private boolean cancel;
	private boolean hasOpened;
	private final Screen previousGuiScreen;
	private int timer = 0;

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, ServerData parServerData) {
		this(parGuiScreen, mcIn, parServerData, false);
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, ServerData parServerData, boolean allowPlaintext) {
		this(parGuiScreen, mcIn, parServerData, null, allowPlaintext);
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, ServerData parServerData, String password) {
		this(parGuiScreen, mcIn, parServerData, password, false);
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, ServerData parServerData, String password,
			boolean allowPlaintext) {
		this.minecraft = mcIn;
		this.previousGuiScreen = parGuiScreen;
		String serveraddress = AddressResolver.resolveURI(parServerData);
		mcIn.setLevel((ClientLevel) null);
		mcIn.setCurrentServer(parServerData);
		if (RateLimitTracker.isLockedOut(serveraddress)) {
			logger.error("Server locked this client out on a previous connection, will not attempt to reconnect");
		} else {
			// EaglerClientState.current() reads the state hung off Minecraft.getConnection(),
			// and this constructor runs to *open* that connection - so there is none yet and it
			// returns null. Dereferencing it threw before a single byte was sent, which is why
			// this has to default rather than ask: the cookie preference it wants belongs to a
			// connection that has not happened.
			//
			// The configuration value is the right default. It is what the no-ServerData
			// constructor below already passes, and enableCookies only ever narrows it.
			// Guarded on getConnection() rather than on the state object, which is the same
			// shape GuiScreenEditCape already uses: EaglerClientState.current() is just
			// get(Minecraft.getInstance().getConnection()), so a null connection is the real
			// condition and asking it directly avoids naming the state type here at all.
			this.connect(serveraddress, password, allowPlaintext,
					EagRuntime.getConfiguration().isEnableServerCookies()
							&& (mcIn.getConnection() == null
									|| net.lax1dude.eaglercraft.compat.EaglerClientState.current().enableCookies));
		}
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, String hostName, int port) {
		this(parGuiScreen, mcIn, hostName, port, false, EagRuntime.getConfiguration().isEnableServerCookies());
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, String hostName, int port, boolean allowCookies) {
		this(parGuiScreen, mcIn, hostName, port, false, allowCookies);
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, String hostName, int port, boolean allowPlaintext,
			boolean allowCookies) {
		this(parGuiScreen, mcIn, hostName, port, null, allowPlaintext, allowCookies);
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, String hostName, int port, String password,
			boolean allowCookies) {
		this(parGuiScreen, mcIn, hostName, port, password, false, allowCookies);
	}

	public GuiConnecting(Screen parGuiScreen, Minecraft mcIn, String hostName, int port, String password,
			boolean allowPlaintext, boolean allowCookies) {
		this.minecraft = mcIn;
		this.previousGuiScreen = parGuiScreen;
		mcIn.setLevel((ClientLevel) null);
		this.connect(hostName, password, allowPlaintext,
				allowCookies && EagRuntime.getConfiguration().isEnableServerCookies());
	}

	public GuiConnecting(GuiConnecting previous, String password) {
		this(previous, password, false);
	}

	public GuiConnecting(GuiConnecting previous, String password, boolean allowPlaintext) {
		this.minecraft = previous.minecraft;
		this.previousGuiScreen = previous.previousGuiScreen;
		this.connect(previous.currentAddress, password, allowPlaintext, previous.allowCookies);
	}

	private void connect(String ip, String password, boolean allowPlaintext, boolean allowCookies) {
		this.currentAddress = ip;
		this.currentPassword = password;
		this.allowPlaintext = allowPlaintext;
		this.allowCookies = allowCookies;
	}

	/**+
	 * Called from the main game loop to update the screen.
	 */
	public void updateScreen() {
		++timer;
		if (timer > 1) {
			if (this.currentAddress == null) {
				minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("Connection Refused"), new TextComponent("Too many connections, try again later")));
			} else if (webSocket == null) {
				logger.info("Connecting to: {}", currentAddress);
				webSocket = PlatformNetworking.openWebSocket(currentAddress);
				if (webSocket == null) {
					minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("connect.failed"),
							new TextComponent("Could not open WebSocket to \"" + currentAddress + "\"!")));
				}
			} else {
				if (webSocket.getState() == EnumEaglerConnectionState.CONNECTED) {
					if (!hasOpened) {
						hasOpened = true;
						logger.info("Logging in: {}", currentAddress);
						byte[] cookieData = null;
						if (allowCookies) {
							ServerCookieDataStore.ServerCookie cookie = ServerCookieDataStore
									.loadCookie(currentAddress);
							if (cookie != null) {
								cookieData = cookie.cookie;
							}
						}
						if (ConnectionHandshake.attemptHandshake(this.minecraft, webSocket, this, previousGuiScreen,
								currentPassword, allowPlaintext, allowCookies, cookieData)) {
							logger.info("Handshake Success");
							webSocket.setEnableStringFrames(false);
							webSocket.clearStringFrames();
							this.networkManager = new WebSocketNetworkManager(webSocket);
							this.networkManager.setPluginInfo(ConnectionHandshake.pluginBrand,
									ConnectionHandshake.pluginVersion);
							//TODO
							//minecraft.bungeeOutdatedMsgTimer = 80;
							// 1.12.2 cleared the title overlay here; 1.18.2 spells it resetTitleTimes().
							minecraft.gui.resetTitleTimes();
							this.networkManager.setConnectionState(ConnectionProtocol.PLAY);
							ClientPacketListener netHandler = new ClientPacketListener(this.minecraft,
									previousGuiScreen, this.networkManager,
									this.minecraft.getUser().getGameProfile(),
									this.minecraft.createTelemetryManager());
							this.networkManager.setListener(netHandler);
							net.lax1dude.eaglercraft.compat.EaglerClientState.get(netHandler).setEaglerMessageController(new GameProtocolMessageController(
									GamePluginMessageProtocol.getByVersion(ConnectionHandshake.protocolVersion),
									GamePluginMessageConstants.CLIENT_TO_SERVER,
									GameProtocolMessageController
											.createClientHandler(ConnectionHandshake.protocolVersion, netHandler),
									// The channel has to be translated, not wrapped: EaglercraftX's names
									// ("EAG|Skins-1.8") are illegal as a 1.18.2 ResourceLocation and the
									// constructor throws on them. See EaglerPluginChannels.
									(ch, msg) -> {
										net.minecraft.resources.ResourceLocation chId =
												net.lax1dude.eaglercraft.compat.EaglerPluginChannels.toId(ch);
										if (chId == null) {
											logger.warn("Not sending a message on unknown EaglercraftX channel {}", ch);
											return;
										}
										netHandler.send(new ServerboundCustomPayloadPacket(chId, msg));
									}));
						} else {
							if (minecraft.screen == this) {
								checkRatelimit();
								logger.info("Handshake Failure");
								// 1.18.2's User is immutable; signing out replaces it rather than resetting it.
								minecraft.setScreen(
										new DisconnectedScreen(previousGuiScreen, new TextComponent("connect.failed"), new TextComponent(
												"Handshake Failure\n\nAre you sure this is an eagler 1.12 server?")));
							}
							webSocket.close();
							return;
						}
					}
					if (this.networkManager != null) {
						try {
							this.networkManager.processReceivedPackets();
						} catch (IOException ex) {
						}
					}
				} else {
					if (webSocket.getState() == EnumEaglerConnectionState.FAILED) {
						if (!hasOpened) {
							// 1.18.2's User is immutable; signing out replaces it rather than resetting it.
							checkRatelimit();
							if (minecraft.screen == this) {
								if (RateLimitTracker.isProbablyLockedOut(currentAddress)) {
									minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("Connection Refused"), new TextComponent("Too many connections, try again later")));
								} else {
									minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("connect.failed"),
											new TextComponent("Connection Refused")));
								}
							}
						}
					} else {
						if (this.networkManager != null && this.networkManager.checkDisconnected()) {
							// 1.18.2's User is immutable; signing out replaces it rather than resetting it.
							checkRatelimit();
							if (minecraft.screen == this) {
								if (RateLimitTracker.isProbablyLockedOut(currentAddress)) {
									minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("Connection Refused"), new TextComponent("Too many connections, try again later")));
								} else {
									minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("connect.failed"),
											new TextComponent("Connection Refused")));
								}
							}
						}
					}
				}
			}
			if (timer > 200) {
				if (webSocket != null) {
					webSocket.close();
				}
				minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("connect.failed"),
						new TextComponent("Handshake timed out")));
			}
		}
	}

	/**+
	 * Fired when a key is typed (except F11 which toggles full
	 * screen). This is the equivalent of
	 * KeyListener.keyTyped(KeyEvent e). Args : character (character
	 * on the key), keyCode (lwjgl Keyboard key code)
	 */
	protected void keyTyped(char parChar1, int parInt1) {
	}

	/**+
	 * Adds the buttons (and other controls) to the screen in
	 * question. Called when the GUI is displayed and when the
	 * window resizes, the buttonList is cleared beforehand.
	 */
	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(
				new GuiButtonCompat(0, this.width / 2 - 100, this.height / 2 - 10, I18n.get("gui.cancel", new Object[0])));
	}

	/**+
	 * Called by the controls from the buttonList when activated.
	 * (Mouse pressed for buttons)
	 */
	protected void actionPerformed(GuiButtonCompat parGuiButton) {
		if (parGuiButton.id == 0) {
			this.cancel = true;
			if (this.networkManager != null) {
				this.networkManager.closeChannel(new TextComponent("Aborted"));
			} else if (this.webSocket != null) {
				this.webSocket.close();
			}

			this.minecraft.setScreen(this.previousGuiScreen);
		}

	}

	/**+
	 * Draws the screen and all the components in it. Args : mouseX,
	 * mouseY, renderPartialTicks
	 */
	public void drawScreen(int i, int j, float f) {
		this.drawDefaultBackground();
		if (this.networkManager == null || !this.networkManager.isChannelOpen()) {
			this.drawCenteredString(this.font, I18n.get("connect.connecting", new Object[0]),
					this.width / 2, this.height / 2 - 50, 16777215);
		} else {
			this.drawCenteredString(this.font, I18n.get("connect.authorizing", new Object[0]),
					this.width / 2, this.height / 2 - 50, 16777215);
		}

		super.drawScreen(i, j, f);
	}

	private void checkRatelimit() {
		if (this.webSocket != null) {
			List<IWebSocketFrame> strFrames = webSocket.getNextStringFrames();
			if (strFrames != null) {
				for (int i = 0; i < strFrames.size(); ++i) {
					String str = strFrames.get(i).getString();
					if (str.equalsIgnoreCase("BLOCKED")) {
						RateLimitTracker.registerBlock(currentAddress);
						minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("Connection Refused"), new TextComponent("Too many connections, try again later")));
						logger.info("Handshake Failure: Too Many Requests!");
					} else if (str.equalsIgnoreCase("LOCKED")) {
						RateLimitTracker.registerLockOut(currentAddress);
						minecraft.setScreen(new DisconnectedScreen(previousGuiScreen, new TextComponent("Connection Refused"), new TextComponent("Too many connections, try again later")));
						logger.info("Handshake Failure: Too Many Requests!");
						logger.info("Server has locked this client out");
					}
				}
			}
		}
	}

	public boolean canCloseGui() {
		return false;
	}
}