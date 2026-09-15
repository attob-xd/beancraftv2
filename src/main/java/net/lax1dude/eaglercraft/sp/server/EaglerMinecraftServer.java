package net.lax1dude.eaglercraft.sp.server;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import net.lax1dude.eaglercraft.compat.EaglerWorldSettings;

import org.apache.logging.log4j.Logger;

import com.google.common.collect.Lists;

import net.lax1dude.eaglercraft.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.sp.server.skins.IntegratedCapeService;
import net.lax1dude.eaglercraft.sp.server.skins.IntegratedSkinService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelStorageSource;

public class EaglerMinecraftServer extends MinecraftServer {

	public static final Logger logger = EaglerIntegratedServerWorker.logger;

	protected Difficulty difficulty;
	protected GameType gamemode;
	protected EaglerWorldSettings newWorldSettings;
	protected IntegratedSkinService skinService;
	protected IntegratedCapeService capeService;

	public static int counterTicksPerSecond = 0;
	public static int counterChunkRead = 0;
	public static int counterChunkGenerate = 0;
	public static int counterChunkWrite = 0;
	public static int counterTileUpdate = 0;
	public static int counterLightUpdate = 0;

	private final List<Runnable> scheduledTasks = new LinkedList();

	private long lastTPSUpdate = 0l;

	// The browser server runs its own loop rather than MinecraftServer.runServer(), so
	// the loop's clock and pause state live here. 1.18.2 made the vanilla equivalents
	// (nextTickTime, lastOverloadWarning, running) private.
	protected long currentTime = 0L;
	protected long timeOfLastWarning = 0L;
	protected boolean serverRunning = false;
	protected boolean isGamePaused = false;

	public EaglerMinecraftServer(String mcDataDir, String folderName, String worldName, String owner,
			int viewDistance, EaglerWorldSettings currentWorldSettings, boolean demo, LevelStorageSource format)
			throws IOException {
		// 1.18.2's MinecraftServer wants ten already-built arguments and super(..) must come
		// first, so the whole set is assembled by EaglerServerContext beforehand.
		this(EaglerServerContext.create(format, folderName, worldName, currentWorldSettings), folderName,
				worldName, owner, viewDistance, currentWorldSettings, demo);
	}

	private EaglerMinecraftServer(EaglerServerContext ctx, String folderName, String worldName, String owner,
			int viewDistance, EaglerWorldSettings currentWorldSettings, boolean demo) {
		super(ctx.serverThread, ctx.storageSource, ctx.packRepository, ctx.worldStem, ctx.proxy,
				ctx.fixerUpper, ctx.sessionService, ctx.profileRepository, ctx.profileCache,
				ctx.progressListenerFactory);
		this.folderName = folderName;
		this.skinService = new IntegratedSkinService(new VFile2(
				ctx.storageSource.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT)
						.toFile().getPath(),
				"eagler/skulls"));
		this.capeService = new IntegratedCapeService();
		this.setSingleplayerName(owner);
		logger.info("server owner: " + owner);
		this.setLevelName(worldName);
		this.setDemo(demo);
		this.setPlayerList(new EaglerPlayerList(this, viewDistance));
		this.newWorldSettings = currentWorldSettings;
		this.isGamePaused = false;
	}

	/** 1.12.2 kept the save folder on the server; 1.18.2 keeps it in LevelStorageAccess. */
	private String folderName;
	private String levelName;

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	public String getLevelName() {
		return levelName;
	}

	public void setLevelName(String levelName) {
		this.levelName = levelName;
	}

	/**
	 * 1.12.2 let the server set a build height. 1.18.2 takes it from the dimension type,
	 * so this is accepted and ignored rather than silently pretending to apply.
	 */
	public void setBuildLimit(int limit) {
	}

	public IntegratedSkinService getSkinService() {
		return skinService;
	}
	
	public IntegratedCapeService getCapeService() {
		return capeService;
	}

	public void setBaseServerProperties(Difficulty difficulty, GameType gamemode) {
		this.difficulty = difficulty;
		this.gamemode = gamemode;
		this.setPvpAllowed(true);
		this.setFlightAllowed(true);
	}

	public void addScheduledTask(Runnable var1) {
		scheduledTasks.add(var1);
	}

	/**
	 * 1.12.2 called this startServer() and the worker still does; 1.18.2 declares
	 * initServer(), so the public name is kept as a thin forwarder.
	 */
	public boolean startServer() throws IOException {
		return initServer();
	}

	@Override
	protected boolean initServer() throws IOException {
		logger.info("Starting integrated eaglercraft server version 1.12.2");
		this.setUsesAuthentication(false);
		this.setPvpAllowed(true);
		this.setFlightAllowed(true);
		// 1.12.2 took the folder/name/settings here; 1.18.2 already has them in the
		// WorldStem that EaglerServerContext built, so loadLevel() needs no arguments.
		this.loadLevel();
		this.setMotd(this.getSingleplayerName() + " - " + this.getLevelName());
		serverRunning = true;
		return true;
	}

	public void mainLoop(boolean singleThreadMode) {
		long k = net.minecraft.Util.getMillis();
		this.sendTPSToClient(k);
		if (isGamePaused) {
			currentTime = k;
			return;
		}

		long j = k - this.currentTime;
		if ((j > (singleThreadMode ? 500L : 2000L)
				&& this.currentTime - this.timeOfLastWarning >= (singleThreadMode ? 5000L : 15000L))) {
			logger.warn(
					"Can\'t keep up! Did the system time change, or is the server overloaded? Running {}ms behind, skipping {} tick(s)",
					new Object[] { Long.valueOf(j), Long.valueOf(j / 50L) });
			j = 100L;
			this.currentTime = k - 100l;
			this.timeOfLastWarning = this.currentTime;
		}

		if (j < 0L) {
			logger.warn("Time ran backwards! Did the system time change?");
			j = 0L;
			this.currentTime = k;
		}

		// The fast-forward-the-night branch. 1.12.2 guarded it with World.areAllPlayersAsleep(),
		// which is false when nobody is connected; Stream.allMatch() on an empty list is
		// vacuously TRUE, so with no players the server ticked once per loop iteration with no
		// 50ms pacing at all - about 8500 TPS, which is what the once-per-second "Autosave
		// started" in the worker log was (tickCount % 6000 firing that often). The emptiness
		// check restores the 1.12.2 meaning.
		if (this.overworld().getLevelData().isRaining() == false && !this.overworld().players().isEmpty()
				&& this.overworld().players().stream().allMatch(net.minecraft.server.level.ServerPlayer::isSleepingLongEnough)) {
			this.currentTime = k;
			this.tickServer(() -> true);
			++counterTicksPerSecond;
		} else {
			if (j > 50L) {
				this.currentTime += 50l;
				this.tickServer(() -> true);
				++counterTicksPerSecond;
			}
		}
	}

	/**
	 * Reads whatever the client sent over the IPC player channels, once per server tick.
	 *
	 * <p>1.12.2 called {@code EaglerIntegratedServerWorker.tick()} from
	 * {@code MinecraftServer.updateTimeLightAndEntities}. 1.18.2 has no such method - the
	 * equivalent is {@code tickChildren}, which ticks {@code ServerConnectionListener}, and
	 * that only walks netty channels. The integrated server has none of those: its players
	 * arrive over {@code IntegratedServerPlayerNetworkManager}, whose {@code tick()} is what
	 * actually drains the received-packet queue.
	 *
	 * <p>Nothing called it. {@code processAsyncMessageQueue} would hand the client's packets
	 * to the right channel and they would sit in its queue for ever, so the login handshake
	 * never got past the {@code ServerboundHelloPacket} the connecting screen sends - which is
	 * exactly the "singleplayer.connecting..." hang, with the worker alive and ticking the
	 * whole time.
	 */
	@Override
	public void tickChildren(java.util.function.BooleanSupplier var1) {
		EaglerIntegratedServerWorker.tick();
		// flushCache() rode along on the same dead 1.12.2 hook, so it never ran either.
		this.skinService.flushCache();
		super.tickChildren(var1);
	}

	/** Dead in 1.18.2 - kept because the 1.12.2 name appears in ported call sites. */
	public void updateTimeLightAndEntities() {
		this.skinService.flushCache();
		super.tickChildren(() -> true);
	}

	protected void sendTPSToClient(long millis) {
		if (millis - lastTPSUpdate > 1000l) {
			lastTPSUpdate = millis;
			if (serverRunning) {
				List<String> lst = Lists.newArrayList("TPS: " + counterTicksPerSecond + "/20",
						"Chunks: " + countChunksLoaded(this.getAllLevels()) + "/" + countChunksTotal(this.getAllLevels()),
						"Entities: " + countEntities(this.getAllLevels()) + "+" + countTileEntities(this.getAllLevels()),
						"R: " + counterChunkRead + ", G: " + counterChunkGenerate + ", W: " + counterChunkWrite,
						"TU: " + counterTileUpdate + ", LU: " + counterLightUpdate);
				int players = countPlayerEntities(this.getAllLevels());
				if (players > 1) {
					lst.add("Players: " + players);
				}
				counterTicksPerSecond = counterChunkRead = counterChunkGenerate = 0;
				counterChunkWrite = counterTileUpdate = counterLightUpdate = 0;
				EaglerIntegratedServerWorker.reportTPS(lst);
			}
		}
	}

	private static int countChunksLoaded(Iterable<ServerLevel> worlds) {
		int i = 0;
		for (ServerLevel world : worlds) {
			if (world != null) {
				i += world.getChunkSource().getLoadedChunksCount();
			}
		}
		return i;
	}

	private static int countChunksTotal(Iterable<ServerLevel> worlds) {
		int i = 0;
		for (ServerLevel world : worlds) {
			if (world != null) {
				// List<Player> players = world.players();
				// for(int l = 0, n = players.size(); l < n; ++l) {
				// i += ((ServerPlayer)players.get(l)).loadedChunks.size();
				// }
				i += world.getChunkSource().getLoadedChunksCount();
			}
		}
		return i;
	}

	private static int countEntities(Iterable<ServerLevel> worlds) {
		int i = 0;
		for (ServerLevel world : worlds) {
			if (world != null) {
				for (java.util.Iterator<?> it = world.getAllEntities().iterator(); it.hasNext(); it.next()) {
					++i;
				}
			}
		}
		return i;
	}

	private static int countTileEntities(Iterable<ServerLevel> worlds) {
		int i = 0;
		for (ServerLevel world : worlds) {
			if (world != null) {
				// 1.18.2 keeps block entities per chunk (blockEntityTickers is private);
				// there is no level-wide count to report here.
			}
		}
		return i;
	}

	private static int countPlayerEntities(Iterable<ServerLevel> worlds) {
		int i = 0;
		for (ServerLevel world : worlds) {
			if (world != null) {
				i += world.players().size();
			}
		}
		return i;
	}

	public void setPaused(boolean p) {
		isGamePaused = p;
		if (!p) {
			currentTime = System.currentTimeMillis();
		}
	}

	public boolean getPaused() {
		return isGamePaused;
	}

	public boolean canStructuresSpawn() {
		// Before the world is loaded there is no overworld to ask, so the pending
		// settings answer instead - 1.12.2 read the same pair off `levels[0]`/null.
		return this.overworld() != null ? this.getWorldData().worldGenSettings().generateFeatures()
				: newWorldSettings.isMapFeaturesEnabled();
	}

	public GameType getGameType() {
		return this.overworld() != null ? this.getWorldData().getGameType()
				: newWorldSettings.getGameType();
	}

	public Difficulty getDifficulty() {
		return difficulty;
	}

	@Override
	public boolean isHardcore() {
		return this.overworld() != null ? this.getWorldData().isHardcore()
				: newWorldSettings.getHardcoreEnabled();
	}

	public int getOpPermissionLevel() {
		return 4;
	}

	public boolean shouldBroadcastRconToOps() {
		return false;
	}

	public boolean shouldBroadcastConsoleToOps() {
		return false;
	}

	@Override
	public boolean isDedicatedServer() {
		return false;
	}

	@Override
	public boolean isCommandBlockEnabled() {
		return true;
	}


	// ---- 1.12.2 MinecraftServer surface, on top of the 1.18.2 names ----
	// The integrated-server worker and the singleplayer controller call these; keeping
	// the old names here is a smaller change than editing every call site.

	public boolean isServerRunning() {
		return this.isRunning();
	}

	public void saveAllWorlds(boolean quiet) {
		this.saveAllChunks(quiet, true, false);
	}

	public net.minecraft.server.players.PlayerList getConfigurationManager() {
		return this.getPlayerList();
	}

	public void setDifficultyLockedForAllWorlds(boolean locked) {
		this.setDifficultyLocked(locked);
	}

	/**
	 * The integrated server has exactly one owner - the player who opened the world - and
	 * 1.18.2 makes this abstract so every server answers it.
	 */
	@Override
	public boolean isSingleplayerOwner(com.mojang.authlib.GameProfile profile) {
		return profile != null && profile.getName() != null
				&& profile.getName().equalsIgnoreCase(this.getSingleplayerName());
	}

	/** Nobody to inform on an integrated server - the owner is the only player. */
	@Override
	public boolean shouldInformAdmins() {
		return false;
	}

	/** {@code playerDataStorage} is protected on MinecraftServer; EaglerPlayerList needs it. */
	public net.minecraft.world.level.storage.PlayerDataStorage getPlayerDataStorage() {
		return this.playerDataStorage;
	}

	/**
	 * 1.18.2 asks whether the world has been opened to LAN. The browser server has no LAN
	 * to open onto - other players reach it through the page's own relay - so it is never
	 * "published" in Minecraft's sense.
	 */
	@Override
	public boolean isPublished() {
		return false;
	}

	/** epoll is a Linux socket backend; the browser has no sockets at all. */
	@Override
	public boolean isEpollEnabled() {
		return false;
	}

	/**
	 * Packet rate limiting protects a public server from flooding. The integrated server's
	 * only client is the same page, so the limit is off.
	 */
	@Override
	public int getRateLimitPacketsPerSecond() {
		return 0;
	}

	/**
	 * Crash-report detail about the host. There is no OS or JVM to describe in a browser,
	 * so the report says what it is instead of pretending.
	 */
	@Override
	public net.minecraft.SystemReport fillServerSystemReport(net.minecraft.SystemReport report) {
		report.setDetail("Server type", "EaglercraftX integrated server (browser)");
		return report;
	}

	/** RCON is a remote console for dedicated servers; the browser server has none. */
	@Override
	public boolean shouldRconBroadcast() {
		return false;
	}

	/** Datapack function permission level; singleplayer runs everything the owner writes. */
	@Override
	public int getFunctionCompilationLevel() {
		return 2;
	}

	/** The world owner is a full operator on their own singleplayer world. */
	@Override
	public int getOperatorUserPermissionLevel() {
		return 4;
	}
}
