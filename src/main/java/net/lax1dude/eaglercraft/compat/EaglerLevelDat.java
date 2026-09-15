package net.lax1dude.eaglercraft.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Reads the few level.dat fields the world converters need, straight from NBT.
 *
 * <p>The 1.12.2 fork used {@code new WorldInfo(nbt)} to parse a level.dat before the world
 * was open. 1.18.2 has no such constructor - {@code LevelData} is an interface on a live
 * world, and parsing goes through {@code PrimaryLevelData.parse(..)}, which needs a
 * DataFixer and a registry access the converter has not built yet. The converters only
 * look at the save version and EaglercraftX's own marker, so those are read directly.
 */
public class EaglerLevelDat {

	/** EaglercraftX stamps this into worlds it has imported. */
	public static final int EAGLER_VERSION_CURRENT = 1;

	/**
	 * The Anvil save version ("version" in level.dat's Data compound). 19133 is Anvil;
	 * 0 means the field is absent, which the converter reads as a 1.5.2-era world.
	 */
	public static int getSaveVersion(CompoundTag data) {
		return data.contains("version", Tag.TAG_ANY_NUMERIC) ? data.getInt("version") : 0;
	}

	/** Matches 1.12.2's {@code WorldInfo.initEaglerVersion}. */
	public static void initEaglerVersion(CompoundTag data) {
		if (!data.contains("eaglerVersionSerial", Tag.TAG_ANY_NUMERIC)) {
			data.putInt("eaglerVersionSerial", EAGLER_VERSION_CURRENT);
		}
	}

	public static int getEaglerVersion(CompoundTag data) {
		return data.contains("eaglerVersionSerial", Tag.TAG_ANY_NUMERIC)
				? data.getInt("eaglerVersionSerial")
				: 0;
	}

	private EaglerLevelDat() {
	}
}
