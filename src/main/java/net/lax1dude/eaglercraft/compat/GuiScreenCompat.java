package net.lax1dude.eaglercraft.compat;

import java.util.ArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

/**
 * The 1.12.2 {@code GuiScreen} surface, reimplemented on top of 1.18.2's {@link Screen}.
 *
 * <p>Between the two versions the GUI API changed in two ways that touch every screen
 * in the Eaglercraft layer: drawing calls all gained a leading {@link PoseStack}, and
 * buttons moved from a {@code buttonList} + {@code actionPerformed(id)} dispatch to
 * per-button callbacks. Rewriting all 27 screens for that would have meant rewriting
 * their layout and click handling by hand; instead this class threads the PoseStack
 * for them and keeps the button list, so the screens stay as EaglercraftX wrote them.
 *
 * <p>The PoseStack given to {@link #render} is stashed for the duration of the frame,
 * which is what lets the parameterless 1.12.2 draw helpers below work.
 */
public abstract class GuiScreenCompat extends Screen {

	/** Buttons added in {@link #initGui()}; registered as widgets after it returns. */
	protected final ButtonList buttonList = new ButtonList();

	/** Valid only for the duration of {@link #render}. */
	protected PoseStack currentPoseStack;

	protected GuiScreenCompat() {
		super(TextComponent.EMPTY);
	}

	protected GuiScreenCompat(Component title) {
		super(title);
	}

	/** An {@code add} that wires each button's press back to {@link #actionPerformed}. */
	protected class ButtonList extends ArrayList<GuiButtonCompat> {
		@Override
		public boolean add(GuiButtonCompat button) {
			// actionPerformed may throw IOException (1.12.2 declared it that way), which a
			// Consumer cannot; the screens handle their own failures, so it is wrapped.
			button.handler = b -> {
				try {
					actionPerformed(b);
				} catch (java.io.IOException ex) {
					throw new RuntimeException(ex);
				}
			};
			return super.add(button);
		}
	}

	@Override
	protected void init() {
		buttonList.clear();
		clearWidgets();
		initGui();
		for (GuiButtonCompat button : buttonList) {
			addRenderableWidget(button);
		}
	}

	/** 1.12.2's screen setup hook. */
	public void initGui() {
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
		currentPoseStack = poseStack;
		EaglerGuiCompat.setCurrentPoseStack(poseStack);
		try {
			drawScreen(mouseX, mouseY, partialTicks);
		} finally {
			EaglerGuiCompat.setCurrentPoseStack(null);
		}
	}

	/**
	 * 1.12.2's per-frame draw. The default renders the vanilla screen (background,
	 * widgets); overrides call {@code super.drawScreen(..)} to get the same.
	 */
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		super.render(currentPoseStack, mouseX, mouseY, partialTicks);
	}

	@Override
	public void tick() {
		updateScreen();
	}

	/** 1.12.2's per-tick hook. */
	public void updateScreen() {
	}

	@Override
	public void removed() {
		onGuiClosed();
	}

	/** 1.12.2's teardown hook. */
	public void onGuiClosed() {
	}

	/** Called when a button in {@link #buttonList} is pressed. */
	protected void actionPerformed(GuiButtonCompat button) throws java.io.IOException {
	}

	/**
	 * 1.12.2 pumped mouse events itself; 1.18.2 delivers them as callbacks, so there is
	 * nothing to pump. Kept so screens that called it still compile.
	 */
	public void handleMouseInput() throws java.io.IOException {
	}

	// ---- draw helpers that lost their implicit PoseStack in 1.18.2 ----

	public void drawDefaultBackground() {
		renderBackground(currentPoseStack);
	}

	public void drawRect(int left, int top, int right, int bottom, int color) {
		fill(currentPoseStack, left, top, right, bottom, color);
	}

	public void drawTexturedModalRect(int x, int y, int u, int v, int width, int height) {
		blit(currentPoseStack, x, y, u, v, width, height);
	}

	public void drawCenteredString(Font font, String text, int x, int y, int color) {
		drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	public void drawString(Font font, String text, int x, int y, int color) {
		drawString(currentPoseStack, font, text, x, y, color);
	}

	/**
	 * 1.12.2's single keyboard hook, reassembled from the two 1.18.2 split it into.
	 *
	 * <p>Without this, ported screens got no keyboard input at all. 1.12.2 screens override
	 * {@code keyTyped(char, int)}; 1.18.2 calls {@code charTyped} for printable characters and
	 * {@code keyPressed} for everything else, and nothing bridged the two - so every text field
	 * on an EaglercraftX screen was dead. The username box on the profile screen was the
	 * visible one: it took focus, drew a caret, and ignored every key. Stock 1.18.2 screens
	 * such as Direct Connection were unaffected, which is why typing worked there and nowhere
	 * else.
	 *
	 * <p>vanilla gets first refusal so buttons, Esc and any registered widget keep their
	 * behaviour; only what it declines is handed to the 1.12.2 hook.
	 *
	 * <p>The key code handed over is the Eagler/LWJGL one, because that is what 1.12.2 screens
	 * compare against ({@code k == 200} is Up). EditBoxCompat converts back to GLFW for the
	 * 1.18.2 EditBox underneath it.
	 */
	@Override
	public boolean charTyped(char typedChar, int modifiers) {
		if (super.charTyped(typedChar, modifiers)) {
			return true;
		}
		keyTyped(typedChar, 0);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (super.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		keyTyped((char) 0, net.lax1dude.eaglercraft.KeyboardConstants.getEaglerKeyFromGLFW(keyCode));
		return true;
	}

	/** 1.12.2's keyboard hook; screens override it, and this default ignores the key. */
	protected void keyTyped(char typedChar, int keyCode) {
	}

	/** 1.12.2's list-background hook, called by GuiSlot before it draws its rows. */
	public void drawBackground(int tint) {
		drawDefaultBackground();
	}

	/** {@code renderComponentHoverEffect} is protected on Screen; widgets need it too. */
	public void renderComponentHover(net.minecraft.network.chat.Style style, int mouseX, int mouseY) {
		renderComponentHoverEffect(currentPoseStack, style, mouseX, mouseY);
	}
}
