package net.lax1dude.eaglercraft.sp.gui;

import java.io.IOException;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EaglercraftVersion;
import net.lax1dude.eaglercraft.profile.EaglerProfile;
import net.lax1dude.eaglercraft.socket.ConnectionHandshake;
import net.lax1dude.eaglercraft.sp.SingleplayerServerController;
import net.lax1dude.eaglercraft.sp.socket.NetHandlerSingleplayerLogin;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.SingleplayerNetworkManager;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.chat.TextComponent;
import net.lax1dude.eaglercraft.compat.GuiScreenCompat;
import net.lax1dude.eaglercraft.compat.GuiButtonCompat;
import net.minecraft.client.gui.screens.Screen;
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
public class GuiScreenSingleplayerConnecting extends GuiScreenCompat {

	private Screen menu;
	private String message;
	private GuiButtonCompat killTask;
	private SingleplayerNetworkManager networkManager = null;
	private int timer = 0;
	
	private long startStartTime;
	private boolean hasOpened = false;
	
	public GuiScreenSingleplayerConnecting(Screen menu, String message) {
		this.menu = menu;
		// drawScreen draws this straight, so it has to arrive translated; the sister screen
		// GuiScreenIntegratedServerBusy translates its own and this one did not, which is why
		// the raw key "singleplayer.connecting" appeared on screen.
		this.message = I18n.get(message);
	}
	
	public void initGui() {
		if(startStartTime == 0) this.startStartTime = EagRuntime.steadyTimeMillis();
		this.buttonList.add(killTask = new GuiButtonCompat(0, this.width / 2 - 100, this.height / 3 + 50, I18n.get("singleplayer.busy.killTask")));
		killTask.active = false;
	}
	
	public void drawScreen(int par1, int par2, float par3) {
		this.drawDefaultBackground();
		float f = 2.0f;
		int top = this.height / 3;
		
		long millis = EagRuntime.steadyTimeMillis();
		
		long dots = (millis / 500l) % 4l;
		this.drawString(font, message + (dots > 0 ? "." : "") + (dots > 1 ? "." : "") + (dots > 2 ? "." : ""), (this.width - this.font.width(message)) / 2, top + 10, 0xFFFFFF);
		
		long elapsed = (millis - startStartTime) / 1000l;
		if(elapsed > 3) {
			this.drawCenteredString(font, "(" + elapsed + "s)", this.width / 2, top + 25, 0xFFFFFF);
		}
		
		super.drawScreen(par1, par2, par3);
	}

	public boolean doesGuiPauseGame() {
		return false;
	}
	
	public void updateScreen() {
		++timer;
		if (timer > 1) {
			if (this.networkManager == null) {
				this.networkManager = SingleplayerServerController.localPlayerNetworkManager;
				this.networkManager.connect();
			} else {
				if (this.networkManager.isChannelOpen()) {
					if (!hasOpened) {
						hasOpened = true;
						this.minecraft.gui.resetTitleTimes();
						this.networkManager.setProtocol(ConnectionProtocol.LOGIN);
						this.networkManager.setListener(new NetHandlerSingleplayerLogin(this.networkManager, this.minecraft, this.menu));
						// The 1.12.2 fork widened the login packet to carry the skin, cape, handshake
						// data and brand UUID inline. 1.18.2's ServerboundHelloPacket takes only the
						// profile, so the profile data goes over the EaglercraftX plugin channel once
						// the connection reaches the play phase (see EaglerClientState).
						this.networkManager.send(
								new ServerboundHelloPacket(this.minecraft.getUser().getGameProfile()));
					}
					try {
						this.networkManager.processReceivedPackets();
					} catch (IOException ex) {
					}
				} else {
					if (this.networkManager.checkDisconnected()) {
						// 1.18.2's User is immutable; signing out replaces it rather than resetting it.
						if (minecraft.screen == this) {
							minecraft.setLevel(null);
							minecraft.setScreen(new DisconnectedScreen(menu, new TranslatableComponent("connect.failed"), new TextComponent("Worker Connection Refused")));
						}
					}
				}
			}
		}
		
		long millis = EagRuntime.steadyTimeMillis();
		if(millis - startStartTime > 6000l && SingleplayerServerController.canKillWorker()) {
			killTask.active = true;
		}
	}

	protected void actionPerformed(GuiButtonCompat par1GuiButton) {
		if(par1GuiButton.id == 0) {
			SingleplayerServerController.killWorker();
			this.minecraft.setLevel((ClientLevel)null);
			// 1.18.2's User is immutable; signing out replaces it rather than resetting it.
			this.minecraft.setScreen(menu);
		}
	}

	public boolean shouldHangupIntegratedServer() {
		return false;
	}

	public boolean canCloseGui() {
		return false;
	}

}
