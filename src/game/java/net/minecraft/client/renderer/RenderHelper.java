package net.minecraft.client.renderer;

import net.lax1dude.eaglercraft.opengl.GlStateManager;
import net.minecraft.world.phys.Vec3;

public class RenderHelper {
	private static final Vec3 LIGHT0_POS = (new Vec3(0.20000000298023224D, 1.0D, -0.699999988079071D)).normalize();
	private static final Vec3 LIGHT1_POS = (new Vec3(-0.20000000298023224D, 1.0D, 0.699999988079071D)).normalize();

	/**
	 * Disables the OpenGL lighting properties enabled by enableStandardItemLighting
	 */
	public static void disableStandardItemLighting() {
		GlStateManager.disableLighting();
		GlStateManager.disableMCLight(0);
		GlStateManager.disableMCLight(1);
		GlStateManager.disableColorMaterial();
	}

	/**
	 * Sets the OpenGL lighting properties to the values used when rendering blocks
	 * as items
	 */
	public static void enableStandardItemLighting() {
		GlStateManager.enableLighting();
		GlStateManager.enableMCLight(0, 0.6f, LIGHT0_POS.x, LIGHT0_POS.y, LIGHT0_POS.z, 0.0D);
		GlStateManager.enableMCLight(1, 0.6f, LIGHT1_POS.x, LIGHT1_POS.y, LIGHT1_POS.z, 0.0D);
		GlStateManager.setMCLightAmbient(0.4f, 0.4f, 0.4f);
		GlStateManager.enableColorMaterial();
	}

	/**
	 * Sets OpenGL lighting for rendering blocks as items inside GUI screens (such
	 * as containers).
	 */
	public static void enableGUIStandardItemLighting() {
		GlStateManager.pushMatrix();
		GlStateManager.rotate(-30.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(165.0F, 1.0F, 0.0F, 0.0F);
		enableStandardItemLighting();
		GlStateManager.popMatrix();
	}
}
