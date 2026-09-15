package net.lax1dude.eaglercraft.opengl.ext.deferred;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.compat.EaglerOptions;
import net.minecraft.client.Minecraft;

/**
 * Builds and tears down the deferred (shaders) pipeline when its settings change.
 *
 * <p>In the 1.12.2 fork this was {@code RenderGlobal.updateDeferredPipeline()}. 1.18.2's
 * {@code LevelRenderer} comes from the jar and cannot gain methods, and the logic is about
 * the pipeline rather than the level renderer anyway, so it lives beside the pipeline here.
 */
public class EaglerDeferredPipelineControl {

	private static final Logger LOGGER = LogManager.getLogger("EaglerDeferredPipeline");

	/**
	 * Rebuilds the pipeline from the current config, or destroys it when shaders are off.
	 *
	 * <p>{@code updateConfig()} derives EVERY {@code is_rendering_*} flag from the settings
	 * and the pack's capabilities - hand-rolling only a few leaves the rest at their field
	 * defaults, which is what made passes like the environment map and realistic water run
	 * with the wrong inputs in the 1.12.2 fork.
	 */
	public static void updateDeferredPipeline() {
		Minecraft mc = Minecraft.getInstance();
		EaglerDeferredConfig dfc = EaglerOptions.deferredShaderConf;
		if (EaglerOptions.shaders && dfc != null) {
			if (dfc.shaderPackInfo != null) {
				dfc.updateConfig();
			}
			if (mc.level != null && !mc.level.dimensionType().hasSkyLight()) {
				dfc.is_rendering_shadowsSun_clamped = 0;
				dfc.is_rendering_lightShafts = false;
				dfc.is_rendering_subsurfaceScattering = false;
			} else {
				int maxDist = mc.options.renderDistance << 4;
				int ss = dfc.is_rendering_shadowsSun;
				while (ss > 1 && (1 << (ss + 3)) > maxDist) {
					--ss;
				}
				dfc.is_rendering_shadowsSun_clamped = ss;
				dfc.is_rendering_lightShafts = ss > 0 && dfc.is_rendering_lightShafts;
				dfc.is_rendering_subsurfaceScattering = ss > 0 && dfc.is_rendering_subsurfaceScattering;
			}
			if (EaglerDeferredPipeline.instance == null) {
				EaglerDeferredPipeline.instance = new EaglerDeferredPipeline(mc);
			}
			try {
				net.lax1dude.eaglercraft.opengl.ext.deferred.program.SharedPipelineShaders.init();
				EaglerDeferredPipeline.instance.rebuild(dfc);
				EaglerDeferredPipeline.isSuspended = false;
			} catch (Throwable ex) {
				LOGGER.error("Could not enable shaders!");
				LOGGER.error(ex);
				EaglerDeferredPipeline.isSuspended = true;
			}
		}
		if (!EaglerOptions.shaders || EaglerDeferredPipeline.isSuspended) {
			teardown();
		}
	}

	/** Destroys the pipeline when shaders are off or have suspended themselves. */
	public static void teardown() {
		try {
			if (EaglerDeferredPipeline.instance != null) {
				EaglerDeferredPipeline.instance.destroy();
				EaglerDeferredPipeline.instance = null;
			}
		} catch (Throwable ex) {
			LOGGER.error("Could not safely disable shaders!");
			LOGGER.error(ex);
		}
		net.lax1dude.eaglercraft.opengl.ext.deferred.program.SharedPipelineShaders.free();
	}

	private EaglerDeferredPipelineControl() {
	}
}
