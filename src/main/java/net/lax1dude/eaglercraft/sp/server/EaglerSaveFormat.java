package net.lax1dude.eaglercraft.sp.server;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;

import net.lax1dude.eaglercraft.internal.vfs2.VFile2;
import net.minecraft.client.AnvilConverterException;
import com.mojang.datafixers.DataFixer;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelSummary;
import net.peyton.eagler.fs.FileUtils;

public class EaglerSaveFormat extends LevelStorageSource {

	/** Kept so the worker can still refer to the saves root by VFile2. */
	public final VFile2 savesDirectory;

	public EaglerSaveFormat(VFile2 savesDirectoryIn, DataFixer dataFixerIn) {
		// 1.18.2's LevelStorageSource takes NIO paths and a backup dir. The browser VFS is
		// mounted under the same names, so the paths line up with what VFile2 sees.
		super(java.nio.file.Paths.get(savesDirectoryIn.getPath()),
				java.nio.file.Paths.get(savesDirectoryIn.getPath(), "..", "backups"), dataFixerIn);
		this.savesDirectory = savesDirectoryIn;
	}

	@Override
	public String getName() {
		return "eagler";
	}



	public List<LevelSummary> getSaveList() throws AnvilConverterException {
		return FileUtils.getSaveList(this.savesDirectory, this);
	}

}
