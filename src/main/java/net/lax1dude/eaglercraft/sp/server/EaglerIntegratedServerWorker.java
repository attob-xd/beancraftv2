package net.lax1dude.eaglercraft.sp.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.lax1dude.eaglercraft.compat.EaglerWorldSettings;

import org.apache.logging.log4j.ILogRedirector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.EagUtils;
import net.lax1dude.eaglercraft.internal.IPCPacketData;
import net.lax1dude.eaglercraft.internal.PlatformAssets;
import net.lax1dude.eaglercraft.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.sp.SingleplayerServerController;
import net.lax1dude.eaglercraft.sp.ipc.*;
import net.lax1dude.eaglercraft.sp.server.export.WorldConverterEPK;
import net.lax1dude.eaglercraft.sp.server.export.WorldConverterMCA;
import net.lax1dude.eaglercraft.sp.server.internal.ServerPlatformSingleplayer;
import net.lax1dude.eaglercraft.sp.server.socket.IntegratedServerPlayerNetworkManager;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.locale.Language;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.peyton.eagler.fs.FileUtils;

public class EaglerIntegratedServerWorker {

	public static final Logger logger = LogManager.getLogger("EaglerIntegratedServer");

	private static EaglerMinecraftServer currentProcess = null;
	private static EaglerWorldSettings newWorldSettings = null;

	private static final Map<String, IntegratedServerPlayerNetworkManager> openChannels = new HashMap();

	private static final IPCPacketManager packetManagerInstance = new IPCPacketManager();
	
	public static LevelStorageSource anvilConverter;

	private static void processAsyncMessageQueue() {
		List<IPCPacketData> pktList = ServerPlatformSingleplayer.recieveAllPacket();
		if (pktList != null) {
			IPCPacketData packetData;
			for (int i = 0, l = pktList.size(); i < l; ++i) {
				packetData = pktList.get(i);
				if (packetData.channel.equals(SingleplayerServerController.IPC_CHANNEL)) {
					IPCPacketBase ipc;
					try {
						ipc = packetManagerInstance.IPCDeserialize(packetData.contents);
					} catch (IOException ex) {
						throw new RuntimeException("Failed to deserialize IPC packet", ex);
					}
					handleIPCPacket(ipc);
				} else {
					IntegratedServerPlayerNetworkManager netHandler = openChannels.get(packetData.channel);
					if (netHandler != null) {
						netHandler.addRecievedPacket(packetData.contents);
					} else {
						logger.error("Recieved packet on channel that does not exist: \"{}\"", packetData.channel);
					}
				}
			}
		}
		
		if (!ServerPlatformSingleplayer.isSingleThreadMode() && ServerPlatformSingleplayer.isTabAboutToCloseWASM()
				&& !isServerStopped()) {
			logger.info("Autosaving worlds because the tab is about to close!");
			currentProcess.getConfigurationManager().saveAll();
			currentProcess.saveAllWorlds(false);
		}
	}

	public static void tick() {
		List<IntegratedServerPlayerNetworkManager> ocs = new ArrayList<>(openChannels.values());
		for (int i = 0, l = ocs.size(); i < l; ++i) {
			ocs.get(i).tick();
		}
	}

	public static EaglerMinecraftServer getServer() {
		return currentProcess;
	}

	public static boolean getChannelExists(String channel) {
		return openChannels.containsKey(channel);
	}

	public static void closeChannel(String channel) {
		IntegratedServerPlayerNetworkManager netmanager = openChannels.remove(channel);
		if (netmanager != null) {
			netmanager.disconnect(new TextComponent("End of stream"));
			sendIPCPacket(new IPCPacket0CPlayerChannel(channel, false));
		}
	}

	private static void startPlayerConnnection(String channel) {
		if (openChannels.containsKey(channel)) {
			logger.error("Tried opening player channel that already exists: {}", channel);
			return;
		}
		if (currentProcess == null) {
			logger.error("Tried opening player channel while server is stopped: {}", channel);
			return;
		}
		IntegratedServerPlayerNetworkManager networkmanager = new IntegratedServerPlayerNetworkManager(channel);
		networkmanager.setProtocol(ConnectionProtocol.LOGIN);
		networkmanager.setListener(new ServerLoginPacketListenerImpl(currentProcess, networkmanager));
		openChannels.put(channel, networkmanager);
	}

	private static void handleIPCPacket(IPCPacketBase ipc) {
		int id = ipc.id();
		try {
			switch (id) {
			case IPCPacket00StartServer.ID: {
				IPCPacket00StartServer pkt = (IPCPacket00StartServer) ipc;
				
				if (!isServerStopped()) {
					currentProcess.stopServer();
				}

				currentProcess = new EaglerMinecraftServer(pkt.mcDataDir, pkt.folderName, pkt.worldName, pkt.ownerName,
						pkt.initialViewDistance, newWorldSettings, pkt.demoMode, anvilConverter);
				currentProcess.setBaseServerProperties(Difficulty.byId(pkt.initialDifficulty),
						newWorldSettings == null ? GameType.SURVIVAL : newWorldSettings.getGameType());
				currentProcess.startServer();

				String[] worlds = FileUtils.worldsList.getAllLines();
				if (worlds == null || (worlds.length == 1 && worlds[0].trim().isEmpty())) {
					FileUtils.worldsList.setAllChars(pkt.folderName);
				} else {
					String[] s = new String[worlds.length + 1];
					s[0] = pkt.folderName;
					System.arraycopy(worlds, 0, s, 1, worlds.length);
					FileUtils.worldsList.setAllChars(String.join("\n", s));
					FileUtils.formatWorldList(FileUtils.worldsList.getAllLines());
				}

				sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacket00StartServer.ID));
				break;
			}
			case IPCPacketMapAssets.ID: {
				IPCPacketMapAssets epk = (IPCPacketMapAssets) ipc;
				PlatformAssets.assets = epk.assets;
				break;
			}
			case IPCPacket01StopServer.ID: {
				if (currentProcess != null) {
					currentProcess.stopServer();
					currentProcess = null;
				}
				sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacket01StopServer.ID));
				break;
			}
			case IPCPacket02InitWorld.ID: {
				tryStopServer();
				IPCPacket02InitWorld pkt = (IPCPacket02InitWorld) ipc;
				newWorldSettings = new EaglerWorldSettings(pkt.seed, GameType.byId(pkt.gamemode),
						pkt.structures, pkt.hardcore);
				// 1.12.2 indexed WorldType.WORLD_TYPES here. 1.18.2 has no such table - terrain
				// presets are datapack-driven - so the id is carried as a name for later.
				newWorldSettings.setTerrainType(Integer.toString(pkt.worldType));
				newWorldSettings.setGeneratorOptions(pkt.worldArgs);
				if (pkt.bonusChest) {
					newWorldSettings.enableBonusChest();
				}
				if (pkt.cheats) {
					newWorldSettings.enableCommands();
				}
				break;
			}
			case IPCPacket03DeleteWorld.ID: {
				break;
			}
			case IPCPacket05RequestData.ID: {
				break;
			}
			case IPCPacket06RenameWorldNBT.ID: {
				break;
			}
			case IPCPacket07ImportWorld.ID: {
				tryStopServer();
				IPCPacket07ImportWorld pkt = (IPCPacket07ImportWorld)ipc;
				try {
					if(pkt.worldFormat == IPCPacket07ImportWorld.WORLD_FORMAT_EAG) {
						WorldConverterEPK.importWorld(pkt.worldData, pkt.worldName);
					}else if(pkt.worldFormat == IPCPacket07ImportWorld.WORLD_FORMAT_MCA) {
						WorldConverterMCA.importWorld(pkt.worldData, pkt.worldName);
					}else {
						throw new IOException("Client requested an unsupported export format!");
					}
					sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacket07ImportWorld.ID));
				}catch(IOException ex) {
					sendIPCPacket(new IPCPacket15Crashed("COULD NOT IMPORT WORLD \"" + pkt.worldName + "\"!!!\n\n" + EagRuntime.getStackTrace(ex) + "\n\nFile is probably corrupt, try a different world"));
					sendTaskFailed();
				}
				break;
			}
			case IPCPacket0ASetWorldDifficulty.ID: {
				IPCPacket0ASetWorldDifficulty pkt = (IPCPacket0ASetWorldDifficulty) ipc;
				if (!isServerStopped()) {
					if (pkt.difficulty == (byte) -1) {
						currentProcess.setDifficultyLockedForAllWorlds(true);
					} else {
						currentProcess.setDifficulty(Difficulty.byId(pkt.difficulty), true);
					}
				} else {
					logger.warn("Client tried to set difficulty while server was stopped");
				}
				break;
			}
			case IPCPacket0BPause.ID: {
				IPCPacket0BPause pkt = (IPCPacket0BPause) ipc;
				if (!isServerStopped()) {
					currentProcess.setPaused(pkt.pause);
					sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacket0BPause.ID));
				} else {
					logger.error("Client tried to {} while server was stopped", pkt.pause ? "pause" : "unpause");
					sendTaskFailed();
				}
				break;
			}
			case IPCPacket0CPlayerChannel.ID: {
				IPCPacket0CPlayerChannel pkt = (IPCPacket0CPlayerChannel) ipc;
				if (!isServerStopped()) {
					if (pkt.open) {
						startPlayerConnnection(pkt.channel);
					} else {
						closeChannel(pkt.channel);
					}
				} else {
					logger.error("Client tried to {} channel server was stopped", pkt.open ? "open" : "close");
				}
				break;
			}
			case IPCPacket0EListWorlds.ID: {
				break;
			}
			case IPCPacket14StringList.ID: {
				IPCPacket14StringList pkt = (IPCPacket14StringList) ipc;
				switch (pkt.opCode) {
				case IPCPacket14StringList.LOCALE:
					// 1.18.2 loads server-side translations itself from the datapacks.
					break;
				default:
					logger.error("Strange string list 0x{} with length{} recieved", Integer.toHexString(pkt.opCode),
							pkt.stringList.size());
					break;
				}
				break;
			}
			case IPCPacket17ConfigureLAN.ID: {
				break;
			}
			case IPCPacket18ClearPlayers.ID: {
				break;
			}
			case IPCPacket19Autosave.ID: {
				if (!isServerStopped()) {
					currentProcess.getConfigurationManager().saveAll();
					currentProcess.saveAllWorlds(false);
					sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacket19Autosave.ID));
				} else {
					logger.error("Client tried to autosave while server was stopped");
					sendTaskFailed();
				}
				break;
			}
			case IPCPacket1BEnableLogging.ID: {
				enableLoggingRedirector(((IPCPacket1BEnableLogging) ipc).enable);
				break;
			}
			default:
				logger.error("IPC packet type 0x{} class \"{}\" was not handled", Integer.toHexString(id),
						ipc.getClass().getSimpleName());
				sendTaskFailed();
				break;
			}
		} catch (Throwable t) {
			logger.error("IPC packet type 0x{} class \"{}\" was not processed correctly", Integer.toHexString(id),
					ipc.getClass().getSimpleName());
			logger.error(t);
			t.printStackTrace();
			sendIPCPacket(new IPCPacket15Crashed(
					"IPC packet type 0x" + Integer.toHexString(id) + " class \"" + ipc.getClass().getSimpleName()
							+ "\" was not processed correctly!\n\n" + EagRuntime.getStackTrace(t)));
			sendTaskFailed();
		}
	}

	public static void enableLoggingRedirector(boolean en) {
		LogManager.logRedirector = en ? new ILogRedirector() {
			@Override
			public void log(String txt, boolean err) {
				sendLogMessagePacket(txt, err);
			}
		} : null;
	}

	public static void sendLogMessagePacket(String txt, boolean err) {
		sendIPCPacket(new IPCPacket1ALoggerMessage(txt, err));
	}

	public static void sendIPCPacket(IPCPacketBase ipc) {
		byte[] pkt;
		try {
			pkt = packetManagerInstance.IPCSerialize(ipc);
		} catch (IOException ex) {
			throw new RuntimeException("Failed to serialize IPC packet", ex);
		}
		ServerPlatformSingleplayer.sendPacket(new IPCPacketData(SingleplayerServerController.IPC_CHANNEL, pkt));
	}

	public static void sendTaskFailed() {
		sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacketFFProcessKeepAlive.FAILURE));
	}

	public static void sendProgress(String updateMessage, float updateProgress) {
		sendIPCPacket(new IPCPacket0DProgressUpdate(updateMessage, updateProgress));
	}

	private static boolean isServerStopped() {
		return currentProcess == null || !currentProcess.isServerRunning();
	}

	/** Where worlds live, and what vanilla's client-side LevelStorageSource lists. */
	public static final String SAVES_DIR = "saves";

	/**
	 * Moves worlds written by an older build out of "eaglercraft/worlds" and into "saves".
	 *
	 * <p>Those worlds were never loadable - the client has always listed "saves" - so this is
	 * not a format migration, it is the first time anything can read them.
	 *
	 * <p>Driven by whether the OLD directory still has anything in it, and it never overwrites a
	 * destination that already exists. The first version asked instead whether "saves" was empty,
	 * which looked equivalent and was not: vanilla's CreateWorldScreen resolves a world name
	 * through LevelStorageSource and leaves a "saves/<name>/session.lock" behind just for being
	 * looked at. One stray lock file made "saves" non-empty and disabled the migration for good,
	 * with every world still sitting unreachable in the old directory. Keyed on the real
	 * question, this is idempotent: once the old directory is empty it returns immediately.
	 */
	private static void migrateLegacyWorldsDirectory() {
		VFile2 legacy = new VFile2(FileUtils.dataDir, "worlds");
		List<VFile2> files = legacy.listFiles(true);
		if (files.isEmpty()) {
			return;
		}
		String prefix = legacy.toString() + "/";
		int moved = 0;
		int skipped = 0;
		int failed = 0;
		for (int i = 0, l = files.size(); i < l; ++i) {
			String from = files.get(i).toString();
			if (!from.startsWith(prefix)) {
				continue;
			}
			String to = SAVES_DIR + "/" + from.substring(prefix.length());
			if (new VFile2(to).exists()) {
				++skipped;
			} else if (files.get(i).renameTo(to)) {
				++moved;
			} else {
				++failed;
			}
		}
		logger.info("Migrated {} file(s) of previously unreachable worlds from {} into {} ({} already present, {} failed)",
				new Object[] { Integer.valueOf(moved), legacy.toString(), SAVES_DIR,
						Integer.valueOf(skipped), Integer.valueOf(failed) });
	}

	private static void tryStopServer() {
		if (!isServerStopped()) {
			currentProcess.stopServer();
		}
		currentProcess = null;
	}

	private static void mainLoop(boolean singleThreadMode) {
		processAsyncMessageQueue();

		if (currentProcess != null) {
			if (currentProcess.isServerRunning()) {
				currentProcess.mainLoop(singleThreadMode);
			}
			if (!currentProcess.isServerRunning()) {
				currentProcess.stopServer();
				currentProcess = null;
				sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacket01StopServer.ID));
			}
		} else {
			if (!singleThreadMode) {
				EagUtils.sleep(50);
			}
		}
	}

	public static void serverMain() {
		try {
			currentProcess = null;
			logger.info("Starting EaglercraftX integrated server worker...");

			// A worker is a separate TeaVM runtime with its own statics, so none of the
			// preamble net.minecraft.client.main.Main runs on the client has happened here.
			// Both of these are needed before anything touches a registry or a version:
			// MappedRegistry's constructor throws "Not bootstrapped" without bootStrap(), and
			// ServerPacksSource's static initialiser asks for the game version, which is what
			// actually failed - "IllegalStateException: Game version not set", thrown out of
			// EaglerMinecraftServer's constructor. The ordering and the CHECK_DATA_FIXER_SCHEMA
			// flag mirror Main exactly; see the comments there for why each one is required.
			SharedConstants.tryDetectVersion();
			SharedConstants.CHECK_DATA_FIXER_SCHEMA = false;
			Bootstrap.bootStrap();
			Bootstrap.validate();

			// The save format has to exist before the first IPCPacket00StartServer, and it did
			// not: the only assignment to anvilConverter was inside the import-world handler,
			// so a normal "create world" or "load world" reached
			// `new EaglerMinecraftServer(..., anvilConverter)` with null and died inside
			// LevelStorageSource.createAccess. That was invisible for as long as nothing ever
			// sent StartServer - the client was using vanilla's in-process server instead, so
			// this branch had never run in this port at all.
			//
			// It is built after the bootstrap above because DataFixers.getDataFixer() reaches
			// SharedConstants.getCurrentVersion().
			// "saves", not "eaglercraft/worlds", and the name is load-bearing.
			//
			// The client and the worker mount the SAME IndexedDB - both call
			// VFile2.setPrimaryFilesystem(Filesystem.getHandleFor(getWorldsDB())) - so the two
			// halves see one filesystem and the world list is supposed to be simply what the
			// worker wrote. WorldSelectionList.loadWorld() is already built on that assumption.
			// But the client's LevelStorageSource is vanilla's, rooted at
			// gameDirectory.resolve("saves"), and Main builds it with new File("."), which
			// TFile.normalize() flattens to "" - so the client lists "saves" while this wrote
			// "eaglercraft/worlds". Two directories, one filesystem, and getLevelList() always
			// came back empty; vanilla's WorldSelectionList then does what it is written to do
			// with an empty list and forwards straight to CreateWorldScreen. That is why the
			// singleplayer menu never showed a world list - not a missing screen, and not lost
			// data: the worlds were on disk the whole time, in a directory nothing read.
			migrateLegacyWorldsDirectory();
			anvilConverter = new EaglerSaveFormat(new VFile2(SAVES_DIR), DataFixers.getDataFixer());
			
//			if(ServerPlatformSingleplayer.getWorldsDatabase().isRamdisk()) {
//				sendIPCPacket(new IPCPacket1CIssueDetected(IPCPacket1CIssueDetected.ISSUE_RAMDISK_MODE));
//			}

			// signal thread startup successful
			sendIPCPacket(new IPCPacketFFProcessKeepAlive(0xFF));
			
			ServerPlatformSingleplayer.setCrashCallbackWASM(EaglerIntegratedServerWorker::sendIntegratedServerCrashWASMCB);

			while (true) {
				mainLoop(false);
				ServerPlatformSingleplayer.immediateContinue();
			}
		} catch (Throwable tt) {
			if (tt instanceof ReportedException) {
				String fullReport = ((ReportedException) tt).getReport().getFriendlyReport();
				logger.error(fullReport);
				sendIPCPacket(new IPCPacket15Crashed(fullReport));
			} else {
				logger.error("Server process encountered a fatal error!");
				tt.printStackTrace();
				String stackTrace = EagRuntime.getStackTrace(tt);
				logger.error(stackTrace);
				sendIPCPacket(new IPCPacket15Crashed("SERVER PROCESS EXITED!\n\n" + stackTrace));
			}
		} finally {
			if (!isServerStopped()) {
				try {
					currentProcess.stopServer();
				} catch (Throwable t) {
					logger.error("Encountered exception while stopping server!");
					logger.error(EagRuntime.getStackTrace(t));
				}
			}
			logger.error("Server process exited!");
			sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacketFFProcessKeepAlive.EXITED));
		}
	}

	public static void singleThreadMain() {
		logger.info("Starting EaglercraftX integrated server worker...");
		if (ServerPlatformSingleplayer.getWorldsDatabase().isRamdisk()) {
			sendIPCPacket(new IPCPacket1CIssueDetected(IPCPacket1CIssueDetected.ISSUE_RAMDISK_MODE));
		}
		sendIPCPacket(new IPCPacketFFProcessKeepAlive(0xFF));
	}

	public static void singleThreadUpdate() {
		mainLoop(true);
	}
	
	public static void sendIntegratedServerCrashWASMCB(String stringValue, boolean terminated) {
		sendIPCPacket(new IPCPacket15Crashed(stringValue));
		if(terminated) {
			sendIPCPacket(new IPCPacketFFProcessKeepAlive(IPCPacketFFProcessKeepAlive.EXITED));
		}
	}

	public static void reportTPS(List<String> texts) {
		sendIPCPacket(new IPCPacket14StringList(IPCPacket14StringList.SERVER_TPS, texts));
	}

}
