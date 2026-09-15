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

package net.lax1dude.eaglercraft.opengl.ext.deferred;

import java.io.IOException;
import net.lax1dude.eaglercraft.compat.EaglerOptions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class ShaderPackInfoReloadListener implements ResourceManagerReloadListener {

	private static final Logger logger = LogManager.getLogger();

	@Override
	public void onResourceManagerReload(ResourceManager mcResourceManager) {
		Minecraft mc = Minecraft.getInstance();
		try {
			EaglerOptions.deferredShaderConf.reloadShaderPackInfo(mcResourceManager);
		}catch(IOException ex) {
			logger.info("Could not reload shader pack info!");
			logger.info(ex);
			logger.info("Shaders have been disabled");
			EaglerOptions.shaders = false;
		}
		// TODO: 1.12's TextureAtlas has no setEnablePBREagler; wiring the PBR atlas
		// into it is a separate job. Until then the pipeline runs with default
		// material values (no per-block normal/specular maps).
		TextureAtlas tm = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
		if(tm != null) {
			// mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).setEnablePBREagler(EaglerOptions.shaders);
		}
	}

}