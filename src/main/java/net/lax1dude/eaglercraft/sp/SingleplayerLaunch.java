package net.lax1dude.eaglercraft.sp;

import net.lax1dude.eaglercraft.compat.EaglerWorldSettings;
import net.lax1dude.eaglercraft.sp.gui.GuiScreenIntegratedServerBusy;
import net.lax1dude.eaglercraft.sp.gui.GuiScreenSingleplayerConnecting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Starts a singleplayer world on the integrated-server <b>worker</b>, and joins it.
 *
 * <p>This is the step that was missing. Every other piece of EaglercraftX's singleplayer had
 * been ported - the worker boots and logs "Starting EaglercraftX integrated server worker",
 * {@link SingleplayerServerController} has the full IPC surface, {@code EaglerMinecraftServer}
 * extends 1.18.2's {@code MinecraftServer}, and {@link GuiScreenSingleplayerConnecting} speaks
 * 1.18.2's login protocol - but nothing ever called
 * {@link SingleplayerServerController#launchEaglercraftServer}. The worker started, sat idle
 * with no world, and the client quietly used vanilla's in-process server instead.
 *
 * <p><b>Why that mattered, and not as an abstract preference.</b> Vanilla's
 * {@code Minecraft.doLoadLevel} calls {@code MinecraftServer.spin}, which is
 * {@code new Thread(...).start()}. On TeaVM that is a <i>green</i> thread: cooperative, sharing
 * the one core with rendering. The server and the client then take turns badly, and every
 * attempt to balance them just moved the starvation around - free-spinning parks starved the
 * renderer and left a world at 15% for ever, while sleeping parks throttled chunk generation
 * about a hundredfold. A Web Worker is a real OS thread, so the two stop competing and the
 * question stops needing an answer.
 *
 * <p>The two-screen sequence is EaglercraftX's: {@link GuiScreenIntegratedServerBusy} waits for
 * the worker to report {@code WORLD_LOADED}, then hands over to
 * {@link GuiScreenSingleplayerConnecting}, which opens the player channel and performs the
 * login handshake over IPC.
 */
public class SingleplayerLaunch {

	private SingleplayerLaunch() {
	}

	/**
	 * Loads a world that already exists on disk.
	 *
	 * <p>{@code settings} is null here on purpose: a null tells
	 * {@code launchEaglercraftServer} not to send {@code IPCPacket02InitWorld}, which is what
	 * distinguishes opening an existing world from creating one. Passing settings for a world
	 * that already exists would re-initialise it.
	 */
	public static void loadWorld(String folderName, String displayName) {
		launch(folderName, displayName, null);
	}

	/** Creates a world and then loads it; the settings are what the worker generates from. */
	public static void createWorld(String folderName, String displayName, EaglerWorldSettings settings) {
		launch(folderName, displayName, settings);
	}

	private static void launch(String folderName, String displayName, EaglerWorldSettings settings) {
		Minecraft minecraft = Minecraft.getInstance();

		// The worker resolves paths against this, and reads and writes the same IndexedDB
		// database ("worlds") that the client's level source lists from - see
		// ServerPlatformSingleplayer, which mounts getClientConfigAdapter().getWorldsDB().
		// That shared mount is why a world created here is visible to both sides.
		String dataDir = minecraft.gameDirectory == null ? "." : minecraft.gameDirectory.getAbsolutePath();
		int viewDistance = minecraft.options.renderDistance;

		SingleplayerServerController.launchEaglercraftServer(dataDir, folderName, displayName, viewDistance, settings);

		Screen connecting = new GuiScreenSingleplayerConnecting(new TitleScreen(), "singleplayer.connecting");
		minecraft.setScreen(new GuiScreenIntegratedServerBusy(
				connecting,
				"singleplayer.busy.startingWorld",
				"singleplayer.failed.startingWorld",
				SingleplayerServerController::isWorldReady));
	}
}
