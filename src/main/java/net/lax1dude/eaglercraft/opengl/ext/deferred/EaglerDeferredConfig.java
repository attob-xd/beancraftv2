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
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import net.minecraft.util.BlockRenderLayer;

import org.json.JSONException;
import org.json.JSONObject;

import net.lax1dude.eaglercraft.EaglerInputStream;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;

public class EaglerDeferredConfig {

	public static final ResourceLocation shaderPackInfoFile = new ResourceLocation("eagler:glsl/deferred/shader_pack_info.json");

	public ShaderPackInfo shaderPackInfo = null;

	/**
	 * Potato mode. Rather than rewriting the individual options it overrides what
	 * actually gets rendered, so a player's own settings survive turning it off.
	 */
	public boolean potatoMode = false;
	public boolean wavingBlocks = true;
	public boolean dynamicLights = true;
	public boolean ssao = true;
	public int shadowsSun = 3;
	public boolean shadowsColored = false;
	public boolean shadowsSmoothed = true;
	public boolean useEnvMap = true;
	public boolean realisticWater = true;
	public boolean lightShafts = false;
	public boolean raytracing = true;
	public boolean lensDistortion = false;
	public boolean lensFlares = true;
	public boolean bloom = false;
	public boolean fxaa = true;
	public boolean subsurfaceScattering = true;

	public boolean is_rendering_wavingBlocks = true;
	public boolean is_rendering_dynamicLights = true;
	public boolean is_rendering_ssao = true;
	public int is_rendering_shadowsSun = 3;
	public int is_rendering_shadowsSun_clamped = 3;
	public boolean is_rendering_shadowsColored = false;
	public boolean is_rendering_shadowsSmoothed = true;
	public boolean is_rendering_useEnvMap = true;
	public boolean is_rendering_realisticWater = true;
	public boolean is_rendering_lightShafts = false;
	public boolean is_rendering_raytracing = true;
	public boolean is_rendering_lensDistortion = false;
	public boolean is_rendering_lensFlares = true;
	public boolean is_rendering_bloom = false;
	public boolean is_rendering_fxaa = true;
	public boolean is_rendering_subsurfaceScattering = true;

	public void readOption(String key, String value) {
		switch(key) {
		case "shaders_deferred_wavingBlocks":
			wavingBlocks = value.equals("true");
			break;
		case "shaders_deferred_dynamicLights":
			dynamicLights = value.equals("true");
			break;
		case "shaders_deferred_ssao":
			ssao = value.equals("true");
			break;
		case "shaders_deferred_shadowsSun":
			shadowsSun = Integer.parseInt(value);
			break;
		case "shaders_deferred_shadowsColored":
			shadowsColored = value.equals("true");
			break;
		case "shaders_deferred_shadowsSmoothed":
			shadowsSmoothed = value.equals("true");
			break;
		case "shaders_deferred_useEnvMap":
			useEnvMap = value.equals("true");
			break;
		case "shaders_deferred_realisticWater":
			realisticWater = value.equals("true");
			break;
		case "shaders_deferred_lightShafts":
			lightShafts = value.equals("true");
			break;
		case "shaders_deferred_raytracing":
			raytracing = value.equals("true");
			break;
		case "shaders_deferred_lensDistortion":
			lensDistortion = value.equals("true");
			break;
		case "shaders_deferred_lensFlares":
			lensFlares = value.equals("true");
			break;
		case "shaders_deferred_bloom":
			bloom = value.equals("true");
			break;
		case "shaders_deferred_fxaa":
			fxaa = value.equals("true");
			break;
		case "shaders_deferred_subsurfaceScattering":
			subsurfaceScattering = value.equals("true");
			break;
		default:
			break;
		}
	}

	public void writeOptions(PrintWriter output) {
		output.println("shaders_deferred_wavingBlocks:" + wavingBlocks);
		output.println("shaders_deferred_dynamicLights:" + dynamicLights);
		output.println("shaders_deferred_ssao:" + ssao);
		output.println("shaders_deferred_shadowsSun:" + shadowsSun);
		output.println("shaders_deferred_shadowsColored:" + shadowsColored);
		output.println("shaders_deferred_shadowsSmoothed:" + shadowsSmoothed);
		output.println("shaders_deferred_useEnvMap:" + useEnvMap);
		output.println("shaders_deferred_realisticWater:" + realisticWater);
		output.println("shaders_deferred_lightShafts:" + lightShafts);
		output.println("shaders_deferred_raytracing:" + raytracing);
		output.println("shaders_deferred_lensDistortion:" + lensDistortion);
		output.println("shaders_deferred_lensFlares:" + lensFlares);
		output.println("shaders_deferred_bloom:" + bloom);
		output.println("shaders_deferred_fxaa:" + fxaa);
		output.println("shaders_deferred_subsurfaceScattering:" + subsurfaceScattering);
	}

	public void reloadShaderPackInfo(ResourceManager mgr) throws IOException {
		Resource res = mgr.getResource(shaderPackInfoFile);
		try(InputStream is = res.getInputStream()) {
			try {
				JSONObject shaderInfoJSON = new JSONObject(new String(EaglerInputStream.inputStreamToBytes(is), StandardCharsets.UTF_8));
				shaderPackInfo = new ShaderPackInfo(shaderInfoJSON);
			}catch(JSONException ex) {
				throw new IOException("Invalid shader pack info json!", ex);
			}
		}
	}

	public void updateConfig() {
		is_rendering_wavingBlocks = wavingBlocks && shaderPackInfo.WAVING_BLOCKS;
		is_rendering_dynamicLights = dynamicLights && shaderPackInfo.DYNAMIC_LIGHTS;
		is_rendering_ssao = ssao && shaderPackInfo.GLOBAL_AMBIENT_OCCLUSION;
		is_rendering_shadowsSun = is_rendering_shadowsSun_clamped = shaderPackInfo.SHADOWS_SUN ? shadowsSun : 0;
		is_rendering_shadowsColored = shadowsColored && shaderPackInfo.SHADOWS_COLORED;
		is_rendering_shadowsSmoothed = shadowsSmoothed && shaderPackInfo.SHADOWS_SMOOTHED;
		is_rendering_useEnvMap = useEnvMap && shaderPackInfo.REFLECTIONS_PARABOLOID;
		is_rendering_realisticWater = realisticWater && shaderPackInfo.REALISTIC_WATER;
		is_rendering_lightShafts = is_rendering_shadowsSun_clamped > 0 && lightShafts && shaderPackInfo.LIGHT_SHAFTS;
		is_rendering_raytracing = shaderPackInfo.SCREEN_SPACE_REFLECTIONS && raytracing;
		is_rendering_lensDistortion = lensDistortion && shaderPackInfo.POST_LENS_DISTORION;
		is_rendering_lensFlares = lensFlares && shaderPackInfo.POST_LENS_FLARES;
		is_rendering_bloom = bloom && shaderPackInfo.POST_BLOOM;
		is_rendering_fxaa = fxaa && shaderPackInfo.POST_FXAA;
		is_rendering_subsurfaceScattering = subsurfaceScattering && is_rendering_shadowsSun_clamped > 0 && shaderPackInfo.SUBSURFACE_SCATTERING;
		if (potatoMode) {
			applyPotatoOverrides();
		}
	}

	/**
	 * Drops the effects that cost the most for the least: the paraboloid env map
	 * (which re-renders the world twice more), screen space reflections, SSAO,
	 * light shafts and long shadow distances.
	 *
	 * Deliberately keeps the cheap things that carry the look - realistic water,
	 * waving blocks, FXAA and short sun shadows - so it still reads as shaders
	 * rather than as vanilla.
	 */
	private void applyPotatoOverrides() {
		// Everything here either renders the scene an extra time or costs a full
		// screen pass, which is what actually hurts on weak integrated GPUs.
		is_rendering_ssao = false;
		is_rendering_useEnvMap = false;          // re-renders the world twice more
		is_rendering_raytracing = false;         // screen space reflections
		is_rendering_lightShafts = false;
		is_rendering_bloom = false;
		is_rendering_lensFlares = false;
		is_rendering_lensDistortion = false;
		is_rendering_subsurfaceScattering = false;
		is_rendering_shadowsColored = false;
		is_rendering_shadowsSmoothed = false;
		is_rendering_dynamicLights = false;
		// Sun shadows draw the whole world again from the sun's point of view, so
		// even the shortest distance is a second geometry pass. Drop it entirely.
		is_rendering_shadowsSun = is_rendering_shadowsSun_clamped = 0;
		// Realistic water adds a mask pass and a surface pass over the whole scene.
		is_rendering_realisticWater = false;
		// FXAA is another full screen pass; on an integrated GPU that is real time.
		is_rendering_fxaa = false;
		// Waving blocks stays on - it is vertex work only, and it is most of what
		// still makes the world look alive once the rest is gone.
	}

}