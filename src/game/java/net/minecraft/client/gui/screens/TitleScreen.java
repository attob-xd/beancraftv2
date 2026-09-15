package net.minecraft.client.gui.screens;

import com.google.common.util.concurrent.Runnables;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Vector3f;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EaglercraftVersion;
import net.lax1dude.eaglercraft.profile.GuiScreenEditProfile;
import net.lax1dude.eaglercraft.sp.gui.GuiScreenIntegratedServerStartup;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/**
 * Vanilla's title screen with the three things a browser cannot do taken out, and
 * EaglercraftX's own menu put back.
 *
 * <p><b>Realms is gone, and that is a crash fix rather than a tidy-up.</b> Vanilla's
 * constructor calls {@code RealmsClient.create()}, {@code init()} builds a
 * {@code RealmsNotificationsScreen} whenever {@code options.realmsNotifications} is set, and
 * {@code tick()} polls a subscription future. All three end at
 * {@code URL.openConnection(Proxy)}, which TeaVM's {@code java.net.URL} does not have - so the
 * first frame of the menu threw {@code NoSuchMethodError} with no message naming Realms. A
 * page has no Mojang session to authenticate with either, so there is nothing to connect to
 * even if the call existed. The Realms button becomes the fork link, as it did in 1.12.2.
 *
 * <p><b>The 32-bit warning is gone</b> for the same family of reasons: it asks
 * {@code Minecraft.is64Bit()} (which inspects {@code sun.arch.data.model}) and then, to decide
 * whether to nag, asks Realms for the player's subscriptions on a background executor. Neither
 * question means anything here.
 *
 * <p><b>Quit is gone and Edit Profile takes its place.</b> {@code Minecraft.stop()} ends in
 * {@code System.exit}, which does not exist in this build; more to the point, a tab is closed
 * by closing the tab. 1.12.2's fork made exactly this swap, and the button it put there is the
 * one that owns the player's name, skin and cape - which is the only place a browser client
 * can set them, because it never signs in to Mojang.
 *
 * <p>Everything else is vanilla's, including the panorama, the logo, the splash and the button
 * geometry, so the screen still looks and lays out like 1.18.2 rather than like 1.8.
 */
public class TitleScreen extends Screen {

	private static final Logger LOGGER = LogUtils.getLogger();

	public static final Component COPYRIGHT_TEXT = new TextComponent("Copyright Mojang AB. Do not distribute!");
	public static final CubeMap CUBE_MAP = new CubeMap(new ResourceLocation("textures/gui/title/background/panorama"));
	private static final ResourceLocation PANORAMA_OVERLAY = new ResourceLocation("textures/gui/title/background/panorama_overlay.png");
	private static final ResourceLocation ACCESSIBILITY_TEXTURE = new ResourceLocation("textures/gui/accessibility.png");
	private static final ResourceLocation MINECRAFT_LOGO = new ResourceLocation("textures/gui/title/minecraft.png");
	private static final ResourceLocation MINECRAFT_EDITION = new ResourceLocation("textures/gui/title/edition.png");

	private final boolean minceraftEasterEgg;

	@Nullable
	private String splash;

	private final PanoramaRenderer panorama = new PanoramaRenderer(CUBE_MAP);
	private final boolean fading;
	private long fadeInStart;

	public TitleScreen() {
		this(false);
	}

	public TitleScreen(boolean fading) {
		super(new TranslatableComponent("narrator.screen.title"));
		this.fading = fading;
		this.minceraftEasterEgg = new Random().nextFloat() < 1.0E-4;
	}

	/**
	 * Vanilla ticks the Realms notification screen and polls the 32-bit warning here; with
	 * both gone there is nothing for the title screen to do between frames.
	 */
	@Override
	public void tick() {
	}

	public static CompletableFuture<Void> preloadResources(TextureManager textures, Executor executor) {
		return CompletableFuture.allOf(
				textures.preload(MINECRAFT_LOGO, executor),
				textures.preload(MINECRAFT_EDITION, executor),
				textures.preload(PANORAMA_OVERLAY, executor),
				CUBE_MAP.preload(textures, executor));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/**
	 * Shown once per page load, before the menu is ever seen - EaglercraftX has no Mojang
	 * account to take a skin from, so the profile is the first thing it asks about.
	 *
	 * <p>1.12.2 did this by wrapping the menu at startup, in Minecraft.startGame:
	 * {@code mainMenu = new GuiScreenEditProfile(mainMenu);} - the profile screen became the
	 * first screen and the menu its continuation. 1.18.2's Minecraft comes from the jar and
	 * picks its own first screen, so the same effect is produced from the other side: the
	 * title screen hands over on its first init and gets itself back when Done is pressed.
	 */
	private static boolean shownProfileScreenThisLaunch = false;

	@Override
	protected void init() {
		if (!shownProfileScreenThisLaunch) {
			shownProfileScreenThisLaunch = true;
			this.minecraft.setScreen(new GuiScreenEditProfile(this));
			return;
		}

		if (this.splash == null) {
			this.splash = this.minecraft.getSplashManager().getSplash();
		}

		int copyrightWidth = this.font.width(COPYRIGHT_TEXT);
		int copyrightX = this.width - copyrightWidth - 2;
		int top = this.height / 4 + 48;

		this.createMenuOptions(top, 24);

		// Bottom row, left to right: language, Options, Edit Profile, accessibility.
		// Vanilla's row is language / Options / Quit / accessibility, and the geometry is
		// unchanged - only the third button's meaning is.
		this.addRenderableWidget(new ImageButton(this.width / 2 - 124, top + 72 + 12, 20, 20, 0, 106, 20,
				Button.WIDGETS_LOCATION, 256, 256,
				b -> this.minecraft.setScreen(new LanguageSelectScreen(this, this.minecraft.options,
						this.minecraft.getLanguageManager())),
				new TranslatableComponent("narrator.button.language")));

		this.addRenderableWidget(new Button(this.width / 2 - 100, top + 72 + 12, 98, 20,
				new TranslatableComponent("menu.options"),
				b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));

		this.addRenderableWidget(new Button(this.width / 2 + 2, top + 72 + 12, 98, 20,
				new TranslatableComponent("menu.editProfile"),
				b -> this.minecraft.setScreen(new GuiScreenEditProfile(this))));

		this.addRenderableWidget(new ImageButton(this.width / 2 + 104, top + 72 + 12, 20, 20, 0, 0, 20,
				ACCESSIBILITY_TEXTURE, 32, 64,
				b -> this.minecraft.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)),
				new TranslatableComponent("narrator.button.accessibility")));

		this.addRenderableWidget(new PlainTextButton(copyrightX, this.height - 10, copyrightWidth, 10,
				COPYRIGHT_TEXT, b -> this.minecraft.setScreen(new WinScreen(false, Runnables.doNothing())),
				this.font));

		this.minecraft.setConnectedToRealms(false);
	}

	/**
	 * Singleplayer, Multiplayer and the fork link.
	 *
	 * <p>Singleplayer does not open vanilla's {@code SelectWorldScreen} directly: the worlds
	 * live in an integrated server that runs in a web worker, and that worker has to be
	 * started and handshaked first. {@code GuiScreenIntegratedServerStartup} does that and
	 * then shows the world list, exactly as it did in 1.12.2.
	 *
	 * <p>Demo mode is not offered. Vanilla's demo branch loads a fixed "Demo_World" through
	 * {@code LevelStorageSource} and reads {@code hasCreatedDemoWorld} from options;
	 * {@code Minecraft.isDemo()} is wired to the {@code GameConfig} this build constructs in
	 * {@code Main.appMain}, which always passes false, so the branch was unreachable code.
	 */
	private void createMenuOptions(int top, int rowHeight) {
		this.addRenderableWidget(new Button(this.width / 2 - 100, top, 200, 20,
				new TranslatableComponent("menu.singleplayer"),
				b -> this.minecraft.setScreen(new GuiScreenIntegratedServerStartup(this))));

		boolean multiplayerAllowed = this.minecraft.allowsMultiplayer();
		this.addRenderableWidget(new Button(this.width / 2 - 100, top + rowHeight, 200, 20,
				new TranslatableComponent("menu.multiplayer"), b -> {
					Screen next = this.minecraft.options.skipMultiplayerWarning
							? new JoinMultiplayerScreen(this)
							: new SafetyScreen(this);
					this.minecraft.setScreen(next);
				})).active = multiplayerAllowed;

		this.addRenderableWidget(new Button(this.width / 2 - 100, top + rowHeight * 2, 200, 20,
				new TranslatableComponent("menu.forkOnGitlab"),
				b -> EagRuntime.openLink(EaglercraftVersion.projectForkURL)));
	}

	@Override
	public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		if (this.fadeInStart == 0L && this.fading) {
			this.fadeInStart = Util.getMillis();
		}

		float fade = this.fading ? (float) (Util.getMillis() - this.fadeInStart) / 1000.0F : 1.0F;
		this.panorama.render(partialTicks, Mth.clamp(fade, 0.0F, 1.0F));

		int logoX = this.width / 2 - 137;
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, PANORAMA_OVERLAY);
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
				this.fading ? Mth.ceil(Mth.clamp(fade, 0.0F, 1.0F)) : 1.0F);
		blit(pose, 0, 0, this.width, this.height, 0.0F, 0.0F, 16, 128, 16, 128);

		float alpha = this.fading ? Mth.clamp(fade - 1.0F, 0.0F, 1.0F) : 1.0F;
		int alphaBits = Mth.ceil(alpha * 255.0F) << 24;
		if ((alphaBits & -67108864) == 0) {
			return;
		}

		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, MINECRAFT_LOGO);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
		if (this.minceraftEasterEgg) {
			this.blitOutlineBlack(logoX, 30, (x, y) -> {
				this.blit(pose, x + 0, y, 0, 0, 99, 44);
				this.blit(pose, x + 99, y, 129, 0, 27, 44);
				this.blit(pose, x + 99 + 26, y, 126, 0, 3, 44);
				this.blit(pose, x + 99 + 26 + 3, y, 99, 0, 26, 44);
				this.blit(pose, x + 155, y, 0, 45, 155, 44);
			});
		} else {
			this.blitOutlineBlack(logoX, 30, (x, y) -> {
				this.blit(pose, x + 0, y, 0, 0, 155, 44);
				this.blit(pose, x + 155, y, 0, 45, 155, 44);
			});
		}

		RenderSystem.setShaderTexture(0, MINECRAFT_EDITION);
		blit(pose, logoX + 88, 67, 0.0F, 0.0F, 98, 14, 128, 16);


		// Vanilla draws one line: "Minecraft <version>". EaglercraftX draws four corners -
		// the Minecraft version and the upstream project bottom-left, the fork and its
		// copyright bottom-right - and that attribution is a condition of lax1dude's licence,
		// not decoration. The strings themselves live in EaglercraftVersion.
		int white = 16777215 | alphaBits;
		String mcVersion = EaglercraftVersion.mainMenuStringA != null
				? EaglercraftVersion.mainMenuStringA
				: "Minecraft " + SharedConstants.getCurrentVersion().getName();
		drawString(pose, this.font, mcVersion, 2, this.height - 20, white);
		drawIfPresent(pose, EaglercraftVersion.mainMenuStringB, 2, this.height - 10, white);
		// Both of these sit a line higher than they look like they should, because vanilla's
		// own copyright PlainTextButton is already right-aligned at height - 10 (see init).
		// Drawing Eaglercraft's attribution at the same y put two different strings on one
		// line, which rendered as unreadable overlapping glyphs rather than as either string.
		drawRightAligned(pose, EaglercraftVersion.mainMenuStringC, this.height - 30, white);
		drawRightAligned(pose, EaglercraftVersion.mainMenuStringD, this.height - 20, white);
		drawIfPresent(pose, EaglercraftVersion.mainMenuStringE, 2, 2, 16777045 | alphaBits);
		drawIfPresent(pose, EaglercraftVersion.mainMenuStringF, 2, 12, 16777045 | alphaBits);

		// Drawn last, after the logo and every corner string, so the splash is on top of
		// all of them - it overlaps the right-hand end of the logo by design, and drawing
		// it earlier let the logo's black outline cut into it.
		if (this.splash != null) {
			pose.pushPose();
			pose.translate(this.width / 2 + 90, 70.0, 0.0);
			pose.mulPose(Vector3f.ZP.rotationDegrees(-20.0F));
			float scale = 1.8F - Mth.abs(Mth.sin((float) (Util.getMillis() % 1000L) / 1000.0F * ((float) Math.PI * 2F)) * 0.1F);
			scale = scale * 100.0F / (this.font.width(this.splash) + 32);
			pose.scale(scale, scale, scale);
			drawCenteredString(pose, this.font, this.splash, 0, -8, 16776960 | alphaBits);
			pose.popPose();
		}

		for (GuiEventListener child : this.children()) {
			if (child instanceof AbstractWidget) {
				((AbstractWidget) child).setAlpha(alpha);
			}
		}

		super.render(pose, mouseX, mouseY, partialTicks);
	}

	private void drawIfPresent(PoseStack pose, String text, int x, int y, int color) {
		if (text != null && !text.isEmpty()) {
			drawString(pose, this.font, text, x, y, color);
		}
	}

	private void drawRightAligned(PoseStack pose, String text, int y, int color) {
		if (text != null && !text.isEmpty()) {
			drawString(pose, this.font, text, this.width - this.font.width(text) - 2, y, color);
		}
	}
}
