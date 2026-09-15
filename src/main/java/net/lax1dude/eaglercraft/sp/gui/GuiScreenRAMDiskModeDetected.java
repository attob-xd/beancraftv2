package net.lax1dude.eaglercraft.sp.gui;

import net.lax1dude.eaglercraft.sp.SingleplayerServerController;
import net.minecraft.client.gui.screens.TitleScreen;
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
public class GuiScreenRAMDiskModeDetected extends GuiScreenCompat {

	private Screen cont;

	public GuiScreenRAMDiskModeDetected(Screen cont) {
		this.cont = cont;
	}

	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(new GuiButtonCompat(0, this.width / 2 - 100, this.height / 6 + 106, I18n.get("singleplayer.ramdiskdetected.continue")));
		this.buttonList.add(new GuiButtonCompat(1, this.width / 2 - 100, this.height / 6 + 136, I18n.get("singleplayer.ramdiskdetected.singleThreadCont")));
	}

	public void drawScreen(int par1, int par2, float par3) {
		this.drawDefaultBackground();
		this.drawCenteredString(font, I18n.get("singleplayer.ramdiskdetected.title"), this.width / 2, 70, 11184810);
		this.drawCenteredString(font, I18n.get("singleplayer.ramdiskdetected.text0"), this.width / 2, 90, 16777215);
		this.drawCenteredString(font, I18n.get("singleplayer.ramdiskdetected.text1"), this.width / 2, 105, 16777215);
		super.drawScreen(par1, par2, par3);
	}

	protected void actionPerformed(GuiButtonCompat par1GuiButton) {
		if(par1GuiButton.id == 0) {
			this.minecraft.setScreen(cont);
		}else if(par1GuiButton.id == 1) {
			SingleplayerServerController.killWorker();
			minecraft.setScreen(new GuiScreenIntegratedServerStartup(new TitleScreen(), true));
		}
	}

}
