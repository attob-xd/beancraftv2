/*
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

package net.lax1dude.eaglercraft.minecraft;

import java.io.IOException;

import net.lax1dude.eaglercraft.Display;
import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.lax1dude.eaglercraft.compat.GuiScreenCompat;

public class GuiScreenVisualViewport extends GuiScreenCompat {

	protected int offsetX;
	protected int offsetY;

	public final void setWorldAndResolution(Minecraft minecraft, int width, int height) {
		Display.wasVisualViewportResized(); // clear state
		offsetX = Display.getVisualViewportX() * width / minecraft.getWindow().getGuiScaledWidth();
		offsetY = Display.getVisualViewportY() * height / minecraft.getWindow().getGuiScaledHeight();
		setWorldAndResolution0(minecraft, Display.getVisualViewportW() * width / minecraft.getWindow().getGuiScaledWidth(),
				Display.getVisualViewportH() * height / minecraft.getWindow().getGuiScaledHeight());
	}

	protected void setWorldAndResolution0(Minecraft minecraft, int width, int height) {
		super.init(minecraft, width, height);
	}

	@Override
	public final void updateScreen() {
		if(Display.wasVisualViewportResized()) {
			setWorldAndResolution(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
		}
		updateScreen0();
	}

	protected void updateScreen0() {
		super.updateScreen();
	}

	@Override
	public final void drawScreen(int i, int j, float var3) {
		i -= offsetX;
		j -= offsetY;
		GlStateManager.pushMatrix();
		GlStateManager.translate(offsetX, offsetY, 0.0f);
		drawScreen0(i, j, var3);
		GlStateManager.popMatrix();
	}

	protected void drawScreen0(int i, int j, float var3) {
		super.drawScreen(i, j, var3);
	}

	protected final void mouseClicked(int parInt1, int parInt2, int parInt3) throws IOException {
		parInt1 -= offsetX;
		parInt2 -= offsetY;
		mouseClicked0(parInt1, parInt2, parInt3);
	}

	protected void mouseClicked0(int parInt1, int parInt2, int parInt3) throws IOException {
		super.mouseClicked(parInt1, parInt2, parInt3);
	}

	protected final void mouseReleased(int i, int j, int k) {
		i -= offsetX;
		j -= offsetY;
		mouseReleased0(i, j, k);
	}

	protected void mouseReleased0(int i, int j, int k) {
		super.mouseReleased(i, j, k);
	}

	protected final void mouseClickMove(int var1, int var2, int var3, long var4) {
		var1 -= offsetX;
		var2 -= offsetY;
		mouseClickMove0(var1, var2, var3, var4);
	}

	protected void mouseClickMove0(int var1, int var2, int var3, long var4) {
		// 1.12.2 mouseClickMove(x, y, button, timeHeld); 1.18.2 mouseDragged carries the
		// drag delta, which the viewport does not use.
		super.mouseDragged(var1, var2, (int) var3, 0.0D, 0.0D);
	}
}