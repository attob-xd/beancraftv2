package net.lax1dude.eaglercraft.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;

/**
 * 1.12.2's {@code WorldSettings}, which 1.18.2 split in two.
 *
 * <p>The singleplayer worker receives one "create world" packet carrying seed, game mode,
 * structures, bonus chest, cheats and world type together — exactly 1.12.2's WorldSettings.
 * 1.18.2 splits that: {@link LevelSettings} keeps the name, game mode, difficulty, cheats
 * and game rules, while the seed, structures, bonus chest and generator live in
 * {@link WorldGenSettings}. This carries the combined set the way the EaglercraftX code
 * expects, and builds both halves on demand.
 */
public class EaglerWorldSettings {

	private long seed;
	private GameType gameType = GameType.SURVIVAL;
	private boolean mapFeatures = true;
	private boolean hardcore;
	private boolean commandsAllowed;
	private boolean bonusChest;
	private String generatorOptions = "";
	private String terrainType = "default";

	public EaglerWorldSettings(long seed, GameType gameType, boolean mapFeatures, boolean hardcore) {
		this.seed = seed;
		this.gameType = gameType;
		this.mapFeatures = mapFeatures;
		this.hardcore = hardcore;
	}

	public long getSeed() {
		return seed;
	}

	public GameType getGameType() {
		return gameType;
	}

	public boolean isMapFeaturesEnabled() {
		return mapFeatures;
	}

	public boolean getHardcoreEnabled() {
		return hardcore;
	}

	public boolean areCommandsAllowed() {
		return commandsAllowed;
	}

	public boolean isBonusChestEnabled() {
		return bonusChest;
	}

	public String getGeneratorOptions() {
		return generatorOptions;
	}

	public String getTerrainType() {
		return terrainType;
	}

	public EaglerWorldSettings enableCommands() {
		this.commandsAllowed = true;
		return this;
	}

	public EaglerWorldSettings enableBonusChest() {
		this.bonusChest = true;
		return this;
	}

	public EaglerWorldSettings setGeneratorOptions(String generatorOptions) {
		this.generatorOptions = generatorOptions;
		return this;
	}

	public EaglerWorldSettings setTerrainType(String terrainType) {
		this.terrainType = terrainType;
		return this;
	}

	/** The half of the settings 1.18.2 keeps in {@link LevelSettings}. */
	public LevelSettings toLevelSettings(String levelName) {
		return new LevelSettings(levelName, gameType, hardcore,
				hardcore ? Difficulty.HARD : Difficulty.NORMAL, commandsAllowed, new GameRules(),
				DataPackConfig.DEFAULT);
	}

	/**
	 * The half 1.18.2 keeps in {@link WorldGenSettings}: seed, structures, bonus chest.
	 *
	 * <p>The dimension set comes from {@code makeDefault}, which builds the vanilla
	 * overworld/nether/end for these registries. 1.12.2's terrain "type" (flat, amplified,
	 * ...) is a datapack-driven world preset in 1.18.2 and is NOT applied here yet -
	 * {@link #getTerrainType()} is carried but unused, so every created world is normal.
	 */
	public WorldGenSettings toWorldGenSettings(RegistryAccess registries) {
		WorldGenSettings defaults = WorldGenSettings.makeDefault(registries);
		return new WorldGenSettings(seed, mapFeatures, bonusChest, defaults.dimensions());
	}
}
