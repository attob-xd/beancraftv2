package net.lax1dude.eaglercraft.webview;

import net.lax1dude.eaglercraft.compat.EaglerGuiCompat;
import java.io.IOException;
import net.lax1dude.eaglercraft.compat.EaglerOptions;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
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
public class GuiScreenPhishingWarning extends GuiScreenCompat {

	public static boolean hasShownMessage = false;

	private static final ResourceLocation beaconGuiTexture = new ResourceLocation("textures/gui/container/beacon.png");

	private Screen cont;
	private boolean mouseOverCheck;
	private boolean hasCheckedBox;

	public GuiScreenPhishingWarning(Screen cont) {
		this.cont = cont;
	}

	public void initGui() {
		this.buttonList.clear();
		this.buttonList.add(new GuiButtonCompat(0, this.width / 2 - 100, this.height / 6 + 134, I18n.get("webviewPhishingWaring.continue")));
	}

	public void drawScreen(int mx, int my, float pt) {
		this.drawDefaultBackground();
		this.drawCenteredString(font, ChatFormatting.BOLD + I18n.get("webviewPhishingWaring.title"), this.width / 2, 70, 0xFF4444);
		this.drawCenteredString(font, I18n.get("webviewPhishingWaring.text0"), this.width / 2, 90, 16777215);
		this.drawCenteredString(font, I18n.get("webviewPhishingWaring.text1"), this.width / 2, 102, 16777215);
		this.drawCenteredString(font, I18n.get("webviewPhishingWaring.text2"), this.width / 2, 114, 16777215);
		
		String dontShowAgain = I18n.get("webviewPhishingWaring.dontShowAgain");
		int w = font.width(dontShowAgain) + 20;
		int ww = (this.width - w) / 2;
		this.drawString(font, dontShowAgain, ww + 20, 137, 0xCCCCCC);
		
		mouseOverCheck = ww < mx && ww + 17 > mx && 133 < my && 150 > my;
		
		if(mouseOverCheck) {
			GlStateManager.color(0.7f, 0.7f, 1.0f, 1.0f);
		}else {
			GlStateManager.color(0.6f, 0.6f, 0.6f, 1.0f);
		}
		
		EaglerGuiCompat.bindGuiTexture(beaconGuiTexture);
		
		EaglerGuiCompat.currentPoseStack().pushPose();
		EaglerGuiCompat.currentPoseStack().scale(0.75f, 0.75f, 0.75f);
		drawTexturedModalRect(ww * 4 / 3, 133 * 4 / 3, 22, 219, 22, 22);
		EaglerGuiCompat.currentPoseStack().popPose();
		
		if(hasCheckedBox) {
			EaglerGuiCompat.currentPoseStack().pushPose();
			GlStateManager.color(1.1f, 1.1f, 1.1f, 1.0f);
			EaglerGuiCompat.currentPoseStack().translate(0.5f, 0.5f, 0.0f);
			drawTexturedModalRect(ww, 133, 90, 222, 16, 16);
			EaglerGuiCompat.currentPoseStack().popPose();
		}
		
		super.drawScreen(mx, my, pt);
	}

	protected void actionPerformed(GuiButtonCompat par1GuiButton) {
		if(par1GuiButton.id == 0) {
			if(hasCheckedBox && !EaglerOptions.hasHiddenPhishWarning) {
				EaglerOptions.hasHiddenPhishWarning = true;
				minecraft.options.save();
			}
			hasShownMessage = true;
			minecraft.setScreen(cont);
		}
	}

	protected void mouseClicked(int mx, int my, int btn) throws IOException {
		if(btn == 0 && mouseOverCheck) {
			hasCheckedBox = !hasCheckedBox;
			minecraft.getSoundManager().play(net.lax1dude.eaglercraft.compat.EaglerSoundCompat.forUI(new ResourceLocation("gui.button.press"), 1.0F));
			return;
		}
		super.mouseClicked(mx, my, btn);
	}

}
