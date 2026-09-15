package net.lax1dude.eaglercraft.sp.server;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.concurrent.CompletableFuture;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;

import net.lax1dude.eaglercraft.compat.EaglerWorldSettings;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * Everything 1.18.2's {@code MinecraftServer} constructor needs, assembled up front.
 *
 * <p>1.12.2's {@code MinecraftServer(File)} took one argument, so EaglercraftX's server
 * could just chain to it. 1.18.2 takes ten - the storage handle, the datapack repository,
 * a fully loaded {@link WorldStem}, the session/profile services and a progress listener -
 * and {@code super(...)} has to be the first statement, so none of that can be built
 * inside the constructor body. This holder is produced by a static factory instead and
 * spread across the super call.
 *
 * <p>Everything runs inline: the browser has one thread, so both executors
 * {@code WorldStem.load} is given are {@code Runnable::run} rather than a pool.
 */
public class EaglerServerContext {

	public final Thread serverThread;
	public final LevelStorageSource.LevelStorageAccess storageSource;
	public final PackRepository packRepository;
	public final WorldStem worldStem;
	public final Proxy proxy;
	public final DataFixer fixerUpper;
	public final MinecraftSessionService sessionService;
	public final GameProfileRepository profileRepository;
	public final GameProfileCache profileCache;
	public final ChunkProgressListenerFactory progressListenerFactory;

	private EaglerServerContext(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource,
			PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer fixerUpper,
			MinecraftSessionService sessionService, GameProfileRepository profileRepository,
			GameProfileCache profileCache, ChunkProgressListenerFactory progressListenerFactory) {
		this.serverThread = serverThread;
		this.storageSource = storageSource;
		this.packRepository = packRepository;
		this.worldStem = worldStem;
		this.proxy = proxy;
		this.fixerUpper = fixerUpper;
		this.sessionService = sessionService;
		this.profileRepository = profileRepository;
		this.profileCache = profileCache;
		this.progressListenerFactory = progressListenerFactory;
	}

	/**
	 * @param newWorldSettings the settings for a world being created; null when opening an
	 *                         existing world, in which case level.dat supplies them.
	 */
	public static EaglerServerContext create(LevelStorageSource format, String folderName,
			String levelName, EaglerWorldSettings newWorldSettings) throws IOException {
		LevelStorageSource.LevelStorageAccess storage = format.createAccess(folderName);
		PackRepository packs = new PackRepository(PackType.SERVER_DATA, new ServerPacksSource(),
				new FolderRepositorySource(storage.getLevelPath(LevelResource.DATAPACK_DIR).toFile(),
						PackSource.WORLD));

		// Both suppliers have to branch on whether this is a new world, and only the second one
		// did. loadFromWorld reads the DataPacks section out of level.dat and throws
		// "Failed to load data pack config" when it comes back null - which is exactly what a
		// world being *created* looks like, because its level.dat does not exist yet. Every
		// "Create New World" therefore died inside EaglerMinecraftServer's constructor, as a
		// CompletionException out of WorldStem.load's join.
		//
		// Vanilla has the same split: CreateWorldScreen passes the pack selection straight
		// through and only the load path reads it from disk. This has no pack-selection UI, so
		// a new world gets the default, which is the vanilla pack and nothing else.
		WorldStem.DataPackConfigSupplier dataPacks = newWorldSettings == null
				? WorldStem.DataPackConfigSupplier.loadFromWorld(storage)
				: () -> net.minecraft.world.level.DataPackConfig.DEFAULT;
		WorldStem.WorldDataSupplier worldData = newWorldSettings == null
				? WorldStem.WorldDataSupplier.loadFromWorld(storage)
				: (resources, packConfig) -> {
					RegistryAccess.Writable registries = RegistryAccess.builtinCopy();
					WorldData data = new PrimaryLevelData(newWorldSettings.toLevelSettings(levelName),
							newWorldSettings.toWorldGenSettings(registries), Lifecycle.stable());
					return Pair.of(data, registries.freeze());
				};

		WorldStem.InitConfig init = new WorldStem.InitConfig(packs,
				Commands.CommandSelection.INTEGRATED, 2, false);
		CompletableFuture<WorldStem> pending = WorldStem.load(init, dataPacks, worldData,
				Runnable::run, Runnable::run);
		WorldStem stem = pending.join();

		// No session or profile service in the browser: the integrated server is always
		// offline-mode, so profile lookups never leave the page.
		GameProfileRepository profiles = new EaglerOfflineProfileRepository();
		GameProfileCache cache = new GameProfileCache(profiles, new File("usercache.json"));
		GameProfileCache.setUsesAuthentication(false);

		return new EaglerServerContext(Thread.currentThread(), storage, packs, stem, Proxy.NO_PROXY,
				DataFixers.getDataFixer(), null, profiles, cache,
				progress -> new net.minecraft.server.level.progress.LoggerChunkProgressListener(progress));
	}
}
