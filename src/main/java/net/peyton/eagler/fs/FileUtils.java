package net.peyton.eagler.fs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;

import net.lax1dude.eaglercraft.EagUtils;
import net.lax1dude.eaglercraft.internal.vfs2.VFile2;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelSummary;

public class FileUtils {
	
	/*
	 * BE CAREFUL WHEN EDITING THIS FILE!!!
	 * 
	 * Literally every single operation in this file that iterates over any sort of list or array
	 * HAS to be done with a for each loop because of a stupid TeaVM bug that caused it to inline
	 * the code when baking in the switch statements that allow it to emulate multiple threads.
	 * This is because it shares the same scope as IndexedDB which causes it to suspend and resume
	 * the TeaVM thread.
	 * 
	 * The reason that this "fix" works is because TeaVM doesn't inline for each loops with
	 * lambda expressions
	 * 
	 * There is also some other random shit that causes TeaVM to break here, so be careful when
	 * modifying something or adding something new to this file.
	 */

	public static final VFile2 worldsList = WorldsDB.newVFile("worlds_list.txt");
	
	public static final String dataDir = "eaglercraft";

	public static List<LevelSummary> getSaveList(VFile2 savesDirectory, LevelStorageSource saveFormat) {
		convertWorldListIfNeeded(savesDirectory);
		// 1.12.2 parsed each level.dat itself through SaveFormat.getWorldInfo(name).
		// 1.18.2 does the whole scan in LevelStorageSource.getLevelList(), including the
		// version check and the "requires conversion" flag this used to derive by hand.
		try {
			return Lists.newArrayList(saveFormat.getLevelList());
		} catch (net.minecraft.world.level.storage.LevelStorageException ex) {
			return Lists.newArrayList();
		}
	}

	private static void convertWorldListIfNeeded(VFile2 savesDirectory) {
		boolean exists = worldsList.exists();
		if (!net.lax1dude.eaglercraft.compat.EaglerOptions.hasWorldListBeenConverted || !exists) {
			if(exists) {
				worldsList.delete();
			}
			convertWorldList(savesDirectory);
			net.lax1dude.eaglercraft.compat.EaglerOptions.hasWorldListBeenConverted = true;
			Minecraft.getInstance().options.save();
		}
	}

	private static void convertWorldList(VFile2 savesDirectory) {
		// 1.18.2 has no Minecraft.loadingScreen to draw progress on; the conversion
		// runs once and is fast enough that the caller's screen stays up.
		List<String> filesList = savesDirectory.listFilenames(true);
		int size = filesList.size();

		if (size > 0) {
			writeWorldFilesToWorldList(filesList, size);
		}
	}

	private static void writeWorldFilesToWorldList(List<String> filesList, int listSize) {
		filesList.forEach(worldPath -> {
			String[] worlds = worldsList.getAllLines();
			if (worlds == null || (worlds.length == 1 && worlds[0].trim().length() <= 0)) {
				worlds = null;
			}

			if (worlds == null) {
				worldsList.setAllChars(worldPath);
			} else {
				String[] s = new String[worlds.length + 1];
				s[0] = worldPath;
				System.arraycopy(worlds, 0, s, 1, worlds.length);
				worldsList.setAllChars(String.join("\n", s));
			}
		});
		convertFileNamesToWorldDir(worldsList.getAllLines());
		formatWorldList(worldsList.getAllLines());
	}
	
	private static void convertFileNamesToWorldDir(String[] files) {
		List<String> filesList = Arrays.asList(files);
		List<String> updatedFilesList = new ArrayList<String>();
		final int[] index = new int[1];
		filesList.forEach(path -> {
			String[] arr = path.split("/");
			List<String> list1 = Arrays.asList(arr);
			list1.forEach(pathPart -> {
				if (index[0] == 2) {
					updatedFilesList.add(pathPart);
				}
				index[0]++;
			});
			index[0] = 0;
		});
		worldsList.setAllChars(String.join("\n", updatedFilesList.toArray(new String[updatedFilesList.size()])));
	}

	public static void formatWorldList(String[] worlds) {
		if(worldsList.exists()) {
			worldsList.delete();
		}
		List<String> list = Arrays.asList(worlds);
		Set<String> set = new HashSet<String>();
		list.forEach(world -> {
			set.add(world);
		});
		String[] newWorldList = set.toArray(new String[set.size()]);
		worldsList.setAllChars(String.join("\n", newWorldList));
	}

	public static void removeWorldIfExists(String worldName) {
		String[] worldsTxt = FileUtils.worldsList.getAllLines();
		if (worldsTxt != null) {
			List<String> newWorlds = new ArrayList<>();
			for (int i = 0; i < worldsTxt.length; ++i) {
				String str = worldsTxt[i];
				if (!str.equalsIgnoreCase(worldName)) {
					newWorlds.add(str);
				}
			}
			FileUtils.worldsList.setAllChars(String.join("\n", newWorlds));
		}
	}
}
