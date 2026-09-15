package net.lax1dude.eaglercraft.notifications;

import net.lax1dude.eaglercraft.compat.EaglerGuiCompat;
import net.lax1dude.eaglercraft.Mouse;
import net.lax1dude.eaglercraft.internal.EnumCursorType;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.lax1dude.eaglercraft.compat.GuiButtonCompat;
import net.minecraft.resources.ResourceLocation;

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
public class GuiButtonNotifBell extends GuiButtonCompat {

	private static final ResourceLocation eaglerTextures = new ResourceLocation("eagler:gui/eagler_gui.png");

	private int unread = 0;

	public GuiButtonNotifBell(int buttonID, int xPos, int yPos) {
		super(buttonID, xPos, yPos, 20, 20, "");
	}

	public void setUnread(int num) {
		unread = num;
	}

	public void func_191745_a(Minecraft minecraft, int i, int j, float p_191745_4_) {
		if (this.visible) {
			minecraft.getTextureManager().bindForSetup(eaglerTextures);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			boolean flag = i >= this.x && j >= this.y && i < this.x + this.width
					&& j < this.y + this.height;
			int k = 0;
			int c = 14737632;
			if (flag) {
				k += this.height;
				c = 16777120;
				Mouse.showCursor(EnumCursorType.HAND);
			}

			drawTexturedModalRect(x, y, unread > 0 ? 116 : 136, k, width, height);
			
			if(unread > 0) {
				EaglerGuiCompat.currentPoseStack().pushPose();
				EaglerGuiCompat.currentPoseStack().translate(x + 15.5f, y + 11.0f, 0.0f);
				if(unread >= 10) {
					EaglerGuiCompat.currentPoseStack().translate(0.0f, 1.0f, 0.0f);
					EaglerGuiCompat.currentPoseStack().scale(0.5f, 0.5f, 0.5f);
				}else {
					EaglerGuiCompat.currentPoseStack().scale(0.75f, 0.75f, 0.75f);
				}
				drawCenteredString(minecraft.font, Integer.toString(unread), 0, 0, c);
				EaglerGuiCompat.currentPoseStack().popPose();
			}
		}
	}
}
