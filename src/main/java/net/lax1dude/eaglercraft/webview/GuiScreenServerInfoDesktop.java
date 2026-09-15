package net.lax1dude.eaglercraft.webview;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.PauseMenuCustomizeState;
import net.lax1dude.eaglercraft.internal.WebViewOptions;
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
public class GuiScreenServerInfoDesktop extends GuiScreenCompat {

	private final Screen parent;
	private final WebViewOptions opts;

	private int timer = 0;
	private boolean hasStarted = false;

	private GuiButtonCompat btnOpen;

	public GuiScreenServerInfoDesktop(Screen parent, WebViewOptions opts) {
		this.parent = parent;
		this.opts = opts;
	}

	public void initGui() {
		buttonList.clear();
		buttonList.add(btnOpen = new GuiButtonCompat(0, (width - 200) / 2, height / 6 + 110, I18n.get("fallbackWebViewScreen.openButton")));
		btnOpen.active = false;
		buttonList.add(new GuiButtonCompat(1, (width - 200) / 2, height / 6 + 140, I18n.get("fallbackWebViewScreen.exitButton")));
	}

	public void updateScreen() {
		++timer;
		if(timer == 2) {
			WebViewOverlayController.endFallbackServer();
			WebViewOverlayController.launchFallback(opts);
		}else if(timer > 2) {
			if(WebViewOverlayController.fallbackRunning()) {
				btnOpen.active = WebViewOverlayController.getFallbackURL() != null;
				hasStarted = true;
			}else {
				btnOpen.active = false;
			}
		}
	}

	public void actionPerformed(GuiButtonCompat button) {
		if(button.id == 0) {
			String link = WebViewOverlayController.getFallbackURL();
			if(link != null) {
				EagRuntime.openLink(link);
			}
		}else if(button.id == 1) {
			minecraft.setScreen(parent);
		}
	}

	public void onGuiClosed() {
		WebViewOverlayController.endFallbackServer();
	}

	public void drawScreen(int mx, int my, float pt) {
		drawDefaultBackground();
		drawCenteredString(font, PauseMenuCustomizeState.serverInfoEmbedTitle, this.width / 2, 70, 16777215);
		drawCenteredString(font, I18n.get("fallbackWebViewScreen.text0"), this.width / 2, 90, 11184810);
		String link = WebViewOverlayController.fallbackRunning() ? WebViewOverlayController.getFallbackURL()
				: I18n.get(hasStarted ? "fallbackWebViewScreen.exited" : "fallbackWebViewScreen.startingUp");
		drawCenteredString(font, link != null ? link : I18n.get("fallbackWebViewScreen.pleaseWait"),
				width / 2, 110, 16777215);
		super.drawScreen(mx, my, pt);
	}

	protected boolean isPartOfPauseMenu() {
		return true;
	}

}
