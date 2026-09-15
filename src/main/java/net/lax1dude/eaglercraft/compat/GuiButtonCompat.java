package net.lax1dude.eaglercraft.compat;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

/**
 * A 1.12.2-shaped button on top of 1.18.2's {@link Button}.
 *
 * <p>1.18.2 replaced the numeric button id + {@code actionPerformed} dispatch with a
 * per-button {@code OnPress} callback, and renamed {@code enabled} to {@code active}
 * and {@code displayString} to the {@code Component} message. The port keeps the
 * EaglercraftX screens written the way they are - dozens of them switch on
 * {@code button.id} - so the id and the dispatch live here instead.
 *
 * <p>{@code Button.onPress} is {@code protected final}, so the press handler cannot be
 * swapped after construction; {@link #onPress()} is overridden instead and routes to a
 * handler that {@link GuiScreenCompat}'s button list installs when the button is added.
 */
public class GuiButtonCompat extends Button {

	/** EaglercraftX draws some buttons with a scaled font. */
	public float fontScale = 1.0F;

	/** The 1.12.2 button id the owning screen switches on. */
	public int id;

	/** Set by {@link GuiScreenCompat}'s button list on add; routes to actionPerformed. */
	Consumer<GuiButtonCompat> handler;

	public GuiButtonCompat(int id, int x, int y, int width, int height, String text) {
		super(x, y, width, height, new TextComponent(text), b -> {
		});
		this.id = id;
	}

	public GuiButtonCompat(int id, int x, int y, int width, int height, Component text) {
		super(x, y, width, height, text, b -> {
		});
		this.id = id;
	}

	/** 1.12.2's short form: 200x20 is the vanilla default button size. */
	public GuiButtonCompat(int id, int x, int y, String text) {
		this(id, x, y, 200, 20, text);
	}

	@Override
	public void onPress() {
		if (handler != null) {
			handler.accept(this);
		}
	}

	public String getDisplayString() {
		return getMessage().getString();
	}

	public void setDisplayString(String text) {
		setMessage(new TextComponent(text));
	}

	/** 1.12.2's hit test; 1.18.2 spells the same thing isMouseOver(double, double). */
	public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
		return this.active && this.visible && isMouseOver(mouseX, mouseY);
	}

	/** 1.12.2's click sound hook. */
	public void playPressSound(SoundManager soundManager) {
		playDownSound(soundManager);
	}

	/** 1.12.2's widget sheet; 1.18.2 calls it WIDGETS_LOCATION. */
	public static final ResourceLocation BUTTON_TEXTURES = WIDGETS_LOCATION;

	// 1.12.2 draw helpers, on the frame's ambient PoseStack (see EaglerGuiCompat).

	public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
		blit(EaglerGuiCompat.currentPoseStack(), x, y, u, v, width, height);
	}

	public void drawRect(int left, int top, int right, int bottom, int color) {
		fill(EaglerGuiCompat.currentPoseStack(), left, top, right, bottom, color);
	}

	public void drawCenteredString(Font font, String text, int x, int y, int color) {
		drawCenteredString(EaglerGuiCompat.currentPoseStack(), font, text, x, y, color);
	}

	/** 1.12.2's hover flag; 1.18.2 renamed the field to isHovered. */
	public boolean isHoveredCompat() {
		return this.isHovered;
	}

	/** 1.12.2's no-arg hover test; 1.18.2 only has isMouseOver(double, double). */
	public boolean isMouseOver() {
		return this.isHovered;
	}

	/** 1.12.2's three-state button sprite row. */
	public int getHoverState(boolean mouseOver) {
		return !this.active ? 0 : (mouseOver ? 2 : 1);
	}

	/** 1.12.2's blit with an explicit sheet size. */
	public void drawModalRectWithCustomSizedTexture(int x, int y, float u, float v, int width, int height,
			int textureWidth, int textureHeight) {
		blit(EaglerGuiCompat.currentPoseStack(), x, y, 0, u, v, width, height, textureWidth, textureHeight);
	}

	public void drawString(Font font, String text, int x, int y, int color) {
		drawString(EaglerGuiCompat.currentPoseStack(), font, text, x, y, color);
	}
}
