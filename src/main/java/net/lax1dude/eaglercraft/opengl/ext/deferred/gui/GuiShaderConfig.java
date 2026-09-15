/*
 * Copyright (c) 2023 lax1dude. All Rights Reserved.
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

package net.lax1dude.eaglercraft.opengl.ext.deferred.gui;

import java.io.IOException;
import java.util.List;
import net.lax1dude.eaglercraft.compat.EaglerOptions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.lax1dude.eaglercraft.opengl.ext.deferred.EaglerDeferredConfig;
import net.lax1dude.eaglercraft.opengl.ext.deferred.program.ShaderSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.lax1dude.eaglercraft.compat.GuiScreenCompat;
import net.lax1dude.eaglercraft.compat.GuiButtonCompat;
import net.minecraft.client.gui.screens.Screen;

public class GuiShaderConfig extends GuiScreenCompat {

	private static final Logger logger = LogManager.getLogger();

	boolean shaderStartState = false;

	private final Screen parent;
	private GuiShaderConfigList listView;

	private String title;
	private GuiButtonCompat enableDisableButton;
	private GuiButtonCompat potatoButton;

	public GuiShaderConfig(Screen parent) {
		this.parent = parent;
		this.shaderStartState = EaglerOptions.shaders;
	}

	public void initGui() {
		this.title = I18n.get("shaders.gui.title");
		this.buttonList.clear();
		this.buttonList.add(enableDisableButton = new GuiButtonCompat(0, width / 2 - 155, height - 30, 150, 20, I18n.get("shaders.gui.enable")
				+ ": " + (EaglerOptions.shaders ? I18n.get("gui.yes") : I18n.get("gui.no"))));
		this.buttonList.add(new GuiButtonCompat(1, width / 2 + 5, height - 30, 150, 20, I18n.get("gui.done")));
		this.buttonList.add(potatoButton = new GuiButtonCompat(2, width / 2 - 155, height - 52, 310, 20, potatoLabel()));
		potatoButton.active = EaglerOptions.shaders;
		if(listView == null) {
			this.listView = new GuiShaderConfigList(this, minecraft);
		}else {
			this.listView.resize();
		}
	}

	protected void actionPerformed(GuiButtonCompat btn) {
		if(btn.id == 0) {
			EaglerOptions.shaders = !EaglerOptions.shaders;
			listView.setAllDisabled(!EaglerOptions.shaders);
			enableDisableButton.setDisplayString(I18n.get("shaders.gui.enable") + ": "
					+ (EaglerOptions.shaders ? I18n.get("gui.yes") : I18n.get("gui.no")));
			potatoButton.active = EaglerOptions.shaders;
		}else if(btn.id == 1) {
			minecraft.setScreen(parent);
		}else if(btn.id == 2) {
			EaglerDeferredConfig conf = EaglerOptions.deferredShaderConf;
			conf.potatoMode = !conf.potatoMode;
			potatoButton.setDisplayString(potatoLabel());
			conf.updateConfig();
			minecraft.options.save();
			net.lax1dude.eaglercraft.opengl.ext.deferred.EaglerDeferredPipelineControl.updateDeferredPipeline();
		}
	}

	private String potatoLabel() {
		return I18n.get("shaders.gui.potato") + ": "
				+ (EaglerOptions.deferredShaderConf.potatoMode ? I18n.get("gui.yes") : I18n.get("gui.no"));
	}

	public void onGuiClosed() {
		if(shaderStartState != EaglerOptions.shaders || listView.isDirty()) {
			minecraft.options.save();
			if(shaderStartState != EaglerOptions.shaders) {
				// 1.12 has no eaglerShowRefreshResources; the reload below still runs.
				minecraft.reloadResourcePacks();
			}else {
				logger.info("Reloading shaders...");
				try {
					EaglerOptions.deferredShaderConf.reloadShaderPackInfo(minecraft.getResourceManager());
				}catch(IOException ex) {
					logger.info("Could not reload shader pack info!");
					logger.info(ex);
					logger.info("Shaders have been disabled");
					EaglerOptions.shaders = false;
					minecraft.reloadResourcePacks();
					return;
				}

				if(EaglerOptions.shaders) {
					ShaderSource.clearCache();
				}

				if (minecraft.levelRenderer != null) {
					minecraft.levelRenderer.allChanged();
				}
			}
		}
	}

	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		listView.handleMouseInput();
	}

	public void handleTouchInput() throws IOException {
	}

	protected void mouseClicked(int parInt1, int parInt2, int parInt3) throws java.io.IOException {
		super.mouseClicked(parInt1, parInt2, parInt3);
		listView.mouseClicked(parInt1, parInt2, parInt3);
	}

	protected void mouseReleased(int i, int j, int k) {
		super.mouseReleased(i, j, k);
		listView.mouseReleased(i, j, k);
	}

	public void drawScreen(int i, int j, float f) {
		this.drawBackground(0);
		listView.drawScreen(i, j, f);
		drawCenteredString(this.font, title, this.width / 2, 15, 16777215);
		super.drawScreen(i, j, f);
		listView.postRender(i, j, f);
	}

	void renderTooltip(List<String> txt, int x, int y) {
		renderTooltip(net.lax1dude.eaglercraft.compat.EaglerGuiCompat.currentPoseStack(),
				txt.stream().map(net.minecraft.network.chat.TextComponent::new)
						.collect(java.util.stream.Collectors.toList()), java.util.Optional.empty(), x, y);
	}

	Font getFontRenderer() {
		return font;
	}

	Minecraft getInstance() {
		return minecraft;
	}
}