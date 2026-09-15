package net.lax1dude.eaglercraft.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

/**
 * 1.12.2's {@code GuiTextField} surface on top of 1.18.2's {@link EditBox}.
 *
 * <p>The 1.12.2 field was driven imperatively — the screen called {@code drawTextBox()}
 * each frame, fed it {@code textboxKeyTyped(char, int)}, and ticked its caret with
 * {@code updateCursorCounter()}. 1.18.2 makes EditBox a widget: it renders through
 * {@code render(PoseStack, ..)}, takes {@code keyPressed}/{@code charTyped}, and ticks
 * itself. These forward the old calls so the EaglercraftX screens keep working.
 */
public class EditBoxCompat extends EditBox {

	public EditBoxCompat(int componentId, Font font, int x, int y, int width, int height) {
		super(font, x, y, width, height, TextComponent.EMPTY);
	}

	public EditBoxCompat(Font font, int x, int y, int width, int height, Component message) {
		super(font, x, y, width, height, message);
	}

	/** 1.12.2's per-frame draw; the frame's PoseStack comes from the compat layer. */
	public void drawTextBox() {
		render(EaglerGuiCompat.currentPoseStack(), 0, 0, 0.0F);
	}

	/**
	 * 1.12.2 delivered one call carrying both the character and the key code. 1.18.2
	 * splits them, so a printable character goes to charTyped and anything else (backspace,
	 * arrows, ctrl+A/C/V/X) goes to keyPressed.
	 */
	public boolean textboxKeyTyped(char typedChar, int keyCode) {
		if (typedChar >= ' ' && typedChar != 127) {
			return charTyped(typedChar, 0);
		}
		// keyCode arrives as the Eagler/LWJGL code the 1.12.2 screens speak; 1.18.2's EditBox
		// wants GLFW. Passing it through untranslated meant backspace and the arrow keys landed
		// on whatever GLFW key happened to share the number.
		return keyPressed(net.lax1dude.eaglercraft.KeyboardConstants.getGLFWKeyFromEagler(keyCode), 0, 0);
	}

	/** 1.12.2's caret blink tick; 1.18.2 calls it tick(). */
	public void updateCursorCounter() {
		tick();
	}

	/** {@code setFocused} is protected on AbstractWidget in 1.18.2. */
	public void setFocusedCompat(boolean focused) {
		setFocus(focused);
	}
}
