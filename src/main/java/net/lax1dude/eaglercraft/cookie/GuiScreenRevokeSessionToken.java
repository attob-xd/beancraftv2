package net.lax1dude.eaglercraft.cookie;

import java.io.IOException;
import java.util.Collections;

import com.google.common.collect.Lists;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.resources.language.I18n;
import net.lax1dude.eaglercraft.compat.GuiScreenCompat;
import net.lax1dude.eaglercraft.compat.GuiButtonCompat;
import net.minecraft.client.gui.screens.Screen;

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
public class GuiScreenRevokeSessionToken extends GuiScreenCompat {
	protected Screen parentScreen;
	private GuiScreenRevokeSessionToken.List list;
	private GuiButtonCompat inspectButton;
	private GuiButtonCompat revokeButton;

	public GuiScreenRevokeSessionToken(Screen parent) {
		this.parentScreen = parent;
	}

	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(this.inspectButton = new GuiButtonCompat(10, this.width / 2 - 154, this.height - 38, 100, 20, I18n.get("revokeSessionToken.inspect")));
		this.buttonList.add(this.revokeButton = new GuiButtonCompat(9, this.width / 2 - 50, this.height - 38, 100, 20, I18n.get("revokeSessionToken.revoke")));
		this.buttonList.add(new GuiButtonCompat(6, this.width / 2 + 54, this.height - 38, 100, 20, I18n.get("gui.done")));
		this.list = new GuiScreenRevokeSessionToken.List(this.minecraft);
		this.list.registerScrollButtons(7, 8);
		updateButtons();
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		this.list.handleMouseInput();
	}

	protected void actionPerformed(GuiButtonCompat parGuiButton) {
		if (parGuiButton.active) {
			switch (parGuiButton.id) {
			case 6:
				this.minecraft.setScreen(this.parentScreen);
				break;
			case 9:
				String s1 = list.getSelectedItem();
				if(s1 != null) {
					ServerCookieDataStore.ServerCookie cookie = ServerCookieDataStore.loadCookie(s1);
					if(cookie != null) {
						this.minecraft.setScreen(new GuiScreenSendRevokeRequest(this, cookie));
					}else {
						this.initGui();
					}
				}
				break;
			case 10:
				String s2 = list.getSelectedItem();
				if(s2 != null) {
					ServerCookieDataStore.ServerCookie cookie = ServerCookieDataStore.loadCookie(s2);
					if(cookie != null) {
						this.minecraft.setScreen(new GuiScreenInspectSessionToken(this, cookie));
					}else {
						this.initGui();
					}
				}
				break;
			default:
				this.list.actionPerformed(parGuiButton);
			}

		}
	}

	protected void updateButtons() {
		inspectButton.active = revokeButton.active = list.getSelectedItem() != null;
	}

	public void drawScreen(int i, int j, float f) {
		this.list.drawScreen(i, j, f);
		this.drawCenteredString(this.font, I18n.get("revokeSessionToken.title"), this.width / 2, 16, 16777215);
		this.drawCenteredString(this.font, I18n.get("revokeSessionToken.note.0"), this.width / 2, this.height - 66, 8421504);
		this.drawCenteredString(this.font, I18n.get("revokeSessionToken.note.1"), this.width / 2, this.height - 56, 8421504);
		super.drawScreen(i, j, f);
	}

	class List extends GuiSlot {
		private final java.util.List<String> cookieNames = Lists.newArrayList();

		public List(Minecraft mcIn) {
			super(mcIn, GuiScreenRevokeSessionToken.this.width, GuiScreenRevokeSessionToken.this.height, 32, GuiScreenRevokeSessionToken.this.height - 75 + 4, 18);
			ServerCookieDataStore.flush();
			cookieNames.addAll(ServerCookieDataStore.getRevokableServers());
			Collections.sort(cookieNames);
		}

		protected int getSize() {
			return this.cookieNames.size();
		}

		protected void elementClicked(int i, boolean var2, int var3, int var4) {
			selectedElement = i;
			GuiScreenRevokeSessionToken.this.updateButtons();
		}

		protected boolean isSelected(int i) {
			return selectedElement == i;
		}

		protected String getSelectedItem() {
			return selectedElement == -1 ? null : cookieNames.get(selectedElement);
		}

		protected int getContentHeight() {
			return this.getSize() * 18;
		}

		protected void drawBackground() {
			GuiScreenRevokeSessionToken.this.drawDefaultBackground();
		}

		protected void func_192637_a(int i, int var2, int j, int var4, int var5, int var6, float p_192637_7_) {
			GuiScreenRevokeSessionToken.this.drawCenteredString(GuiScreenRevokeSessionToken.this.font,
					this.cookieNames.get(i), this.width / 2, j + 1, 16777215);
		}
	}
}