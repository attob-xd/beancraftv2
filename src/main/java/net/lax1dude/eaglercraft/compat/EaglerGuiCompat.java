package net.lax1dude.eaglercraft.compat;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * The PoseStack for the frame being drawn.
 *
 * <p>1.12.2's GUI drawing was immediate: {@code drawRect}, {@code drawTexturedModalRect}
 * and friends took no matrix, and any widget could call them from anywhere. 1.18.2
 * threads a {@link PoseStack} through every draw call instead. The EaglercraftX widgets
 * are written the 1.12.2 way and are not all reachable from a single call chain - a
 * {@code GuiSlot} draws itself from inside a screen's draw, and its entries draw from
 * inside that - so the stack is published here for the duration of the frame rather than
 * threaded through every signature.
 *
 * <p>Set and cleared by {@link GuiScreenCompat#render}; there is one GUI thread and one
 * frame in flight, which is what makes a single slot safe.
 */
public class EaglerGuiCompat {

	private static PoseStack currentPoseStack;

	static void setCurrentPoseStack(PoseStack poseStack) {
		currentPoseStack = poseStack;
	}

	/**
	 * 1.12.2's {@code mc.getTextureManager().bindTexture(tex)} for GUI drawing.
	 *
	 * <p>The ported call maps to {@code bindForSetup}, which binds the texture to a GL unit -
	 * and 1.18.2's GuiComponent.blit does not read that. It draws with whatever
	 * {@code RenderSystem.setShaderTexture(0, ...)} last set, so every ported 1.12.2 GUI that
	 * bound a texture and then blitted got whichever texture happened to be current, usually
	 * the font atlas. That is why the skin dropdown's arrow came out as a smear of glyphs
	 * instead of an arrow, and the same for the notification icons and the credits background.
	 * Binding both ways keeps the setup path intact and makes the blit use the right texture.
	 */
	public static void bindGuiTexture(net.minecraft.resources.ResourceLocation tex) {
		net.minecraft.client.Minecraft.getInstance().getTextureManager().bindForSetup(tex);
		com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tex);
	}

	/** The frame's PoseStack, or null outside a draw. */
	public static PoseStack currentPoseStack() {
		return currentPoseStack;
	}

	private EaglerGuiCompat() {
	}
}
