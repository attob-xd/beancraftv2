package net.lax1dude.eaglercraft.minecraft;

import net.lax1dude.eaglercraft.compat.EaglerGuiCompat;
import static net.lax1dude.eaglercraft.opengl.RealOpenGLEnums.*;

import net.lax1dude.eaglercraft.Mouse;
import net.lax1dude.eaglercraft.internal.EnumCursorType;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
public class GuiButtonWithStupidIcons extends GuiButtonCompat {

	protected ResourceLocation leftIcon;
	protected float leftIconAspect;
	protected ResourceLocation rightIcon;
	protected float rightIconAspect;

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
		super(buttonId, x, y, widthIn, heightIn, buttonText);
	}

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, String buttonText) {
		super(buttonId, x, y, buttonText);
	}

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText,
			ResourceLocation leftIcon, float leftIconAspect, ResourceLocation rightIcon, float rightIconAspect) {
		super(buttonId, x, y, widthIn, heightIn, buttonText);
		this.leftIcon = leftIcon;
		this.leftIconAspect = leftIconAspect;
		this.rightIcon = rightIcon;
		this.rightIconAspect = rightIconAspect;
	}

	public GuiButtonWithStupidIcons(int buttonId, int x, int y, String buttonText, ResourceLocation leftIcon,
			float leftIconAspect, ResourceLocation rightIcon, float rightIconAspect) {
		super(buttonId, x, y, buttonText);
		this.leftIcon = leftIcon;
		this.leftIconAspect = leftIconAspect;
		this.rightIcon = rightIcon;
		this.rightIconAspect = rightIconAspect;
	}

	public ResourceLocation getLeftIcon() {
		return leftIcon;
	}

	public ResourceLocation getRightIcon() {
		return rightIcon;
	}

	public void setLeftIcon(ResourceLocation leftIcon, float aspectRatio) {
		this.leftIcon = leftIcon;
		this.leftIconAspect = aspectRatio;
	}

	public void setRightIcon(ResourceLocation rightIcon, float aspectRatio) {
		this.rightIcon = rightIcon;
		this.rightIconAspect = aspectRatio;
	}

	public void func_191745_a(Minecraft mc, int mouseX, int mouseY, float p_191745_4_) {
		if (this.visible) {
			Font fontrenderer = mc.font;
			EaglerGuiCompat.bindGuiTexture(BUTTON_TEXTURES);
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			this.isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width
					&& mouseY < this.y + this.height;
			if (this.active && this.isHovered) {
				Mouse.showCursor(EnumCursorType.HAND);
			}
			int i = this.getHoverState(this.isHovered);
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0);
			GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			this.drawTexturedModalRect(this.x, this.y, 0, 46 + i * 20, this.width / 2, this.height);
			this.drawTexturedModalRect(this.x + this.width / 2, this.y, 200 - this.width / 2,
					46 + i * 20, this.width / 2, this.height);
			// 1.12.2 called mouseDragged(mc, x, y) each frame to update the hover sprite;
		// 1.18.2 tracks isHovered itself and mouseDragged is a real drag event.
			int j = 14737632;
			if (!this.active) {
				j = 10526880;
			} else if (this.isHovered) {
				j = 16777120;
			}

			int strWidth = fontrenderer.width(getDisplayString());
			int strWidthAdj = strWidth - (leftIcon != null ? (int) (16 * leftIconAspect) : 0)
					+ (rightIcon != null ? (int) (16 * rightIconAspect) : 0);
			this.drawString(fontrenderer, this.getDisplayString(), this.x + (this.width - strWidthAdj) / 2,
					this.y + (this.height - 8) / 2, j);
			if(leftIcon != null) {
				GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
				EaglerGuiCompat.bindGuiTexture(leftIcon);
				EaglerGuiCompat.currentPoseStack().pushPose();
				EaglerGuiCompat.currentPoseStack().translate(this.x + (this.width - strWidthAdj) / 2 - 3 - 16 * leftIconAspect, this.y + 2, 0.0f);
				float f = 16.0f / 256.0f;
				EaglerGuiCompat.currentPoseStack().scale(f * leftIconAspect, f, f);
				this.drawTexturedModalRect(0, 0, 0, 0, 256, 256);
				EaglerGuiCompat.currentPoseStack().popPose();
			}
			if(rightIcon != null) {
				GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
				EaglerGuiCompat.bindGuiTexture(rightIcon);
				EaglerGuiCompat.currentPoseStack().pushPose();
				EaglerGuiCompat.currentPoseStack().translate(this.x + (this.width - strWidthAdj) / 2 + strWidth + 3, this.y + 2, 0.0f);
				float f = 16.0f / 256.0f;
				EaglerGuiCompat.currentPoseStack().scale(f * rightIconAspect, f, f);
				this.drawTexturedModalRect(0, 0, 0, 0, 256, 256);
				EaglerGuiCompat.currentPoseStack().popPose();
			}
		}
	}

}
