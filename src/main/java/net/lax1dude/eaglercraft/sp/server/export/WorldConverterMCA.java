/*
 * Copyright (c) 2022-2024 lax1dude, ayunami2000. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

package net.lax1dude.eaglercraft.sp.server.export;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EaglerInputStream;
import net.lax1dude.eaglercraft.EaglerOutputStream;
import net.lax1dude.eaglercraft.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.sp.server.EaglerChunkPath;
import net.lax1dude.eaglercraft.sp.server.EaglerIntegratedServerWorker;
import net.lax1dude.eaglercraft.sp.server.EaglerSaveFormat;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.chunk.storage.RegionFile;
import net.peyton.eagler.fs.FileUtils;
import net.peyton.eagler.fs.WorldsDB;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.CompoundTag;
import net.lax1dude.eaglercraft.compat.EaglerLevelDat;

public class WorldConverterMCA {

	private static final Logger logger = LogManager.getLogger("WorldConverterMCA");

	public static void importWorld(byte[] archiveContents, String newName) throws IOException {
		logger.info("Importing world \"{}\" from MCA", newName);
		String folderName = newName.replaceAll("[\\./\"]", "_");
		VFile2 worldDir = new VFile2(EaglerIntegratedServerWorker.anvilConverter.createAccess(folderName).getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile().getPath());
		while(WorldsDB.newVFile(worldDir, "level.dat").exists() || WorldsDB.newVFile(worldDir, "level.dat_old").exists()) {
			folderName += "_";
			worldDir = new VFile2(EaglerIntegratedServerWorker.anvilConverter.createAccess(folderName).getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile().getPath());
		}
		List<char[]> fileNames = new ArrayList<>();
		try(ZipInputStream zis = new ZipInputStream(new EaglerInputStream(archiveContents))) {
			ZipEntry folderNameFile = null;
			while((folderNameFile = zis.getNextEntry()) != null) {
				if (folderNameFile.getName().contains("__MACOSX/")) continue;
				if (folderNameFile.isDirectory()) continue;
				String lowerName = folderNameFile.getName().toLowerCase();
				if (!(lowerName.endsWith(".dat") || lowerName.endsWith(".dat_old") || lowerName.endsWith(".mca") || lowerName.endsWith(".mcr"))) continue;
				fileNames.add(folderNameFile.getName().toCharArray());
			}
		}
		boolean convertMap = false;
		final int[] i = new int[] { 0 };
		while(fileNames.get(0).length > i[0] && fileNames.stream().allMatch(w -> w[i[0]] == fileNames.get(0)[i[0]])) i[0]++;
		int folderPrefixOffset = i[0];
		
		CompoundTag info = null;
		boolean isWorld152 = false;
		try(ZipInputStream zis = new ZipInputStream(new EaglerInputStream(archiveContents))) {
			ZipEntry f = null;
			while((f = zis.getNextEntry()) != null) {
				byte[] b;
				int sz = (int)f.getSize();
				if(sz >= 0) {
					b = new byte[sz];
					int j = 0, k;
					while(j < b.length && (k = zis.read(b, j, b.length - j)) != -1) {
						j += k;
					}
				}else {
					b = EaglerInputStream.inputStreamToBytesNoClose(zis);
				}
				String fileName = f.getName().substring(folderPrefixOffset);
				if (fileName.equals("level.dat") || fileName.equals("level.dat_old")) {
					CompoundTag worldDatNBT = NbtIo.readCompressed(new EaglerInputStream(b)).getCompound("Data");
					if(worldDatNBT == null) {
						throw new IOException("Error reading 'Data' from " + fileName);
					}
					info = worldDatNBT;
					int vers = EaglerLevelDat.getSaveVersion(info);
					convertMap = vers != 19133; //TODO
					
					//TODO
					if(vers == 0) { //Assume 1.5.2 world 
						convertMap = false;
						isWorld152 = true;
					}
				}
			}
		}
		
		try(ZipInputStream zis = new ZipInputStream(new EaglerInputStream(archiveContents))) {
			ZipEntry f = null;
			int lastProgUpdate = 0;
			int prog = 0;
			while ((f = zis.getNextEntry()) != null) {
				if (f.getName().contains("__MACOSX/")) continue;
				if (f.isDirectory()) continue;
				String lowerName = f.getName().toLowerCase();
				if (!(lowerName.endsWith(".dat") || lowerName.endsWith(".dat_old") || lowerName.endsWith(".mca") || lowerName.endsWith(".mcr") || lowerName.endsWith(".bmp"))) continue;
				byte[] b;
				int sz = (int)f.getSize();
				if(sz >= 0) {
					b = new byte[sz];
					int j = 0, k;
					while(j < b.length && (k = zis.read(b, j, b.length - j)) != -1) {
						j += k;
					}
				}else {
					b = EaglerInputStream.inputStreamToBytesNoClose(zis);
				}
				String fileName = f.getName().substring(folderPrefixOffset);
				if (fileName.equals("level.dat") || fileName.equals("level.dat_old")) {
					CompoundTag worldDatNBT = NbtIo.readCompressed(new EaglerInputStream(b));
	
					CompoundTag gameRulesNBT = worldDatNBT.getCompound("Data").getCompound("GameRules");
					worldDatNBT.getCompound("Data").put("GameRules", gameRulesNBT);
					worldDatNBT.getCompound("Data").putString("LevelName", newName);
					worldDatNBT.getCompound("Data").putLong("LastPlayed", System.currentTimeMillis());
					worldDatNBT.getCompound("Data").putInt("version", 19133);
					// 1.18.2 identifies a save by Version.Id / DataVersion, not by the
					// name, and refuses to load one newer than itself. Stamping the
					// client's own version tells it the world needs no upgrading - which
					// is only true for a world this build wrote. An older world imported
					// here will NOT be upgraded, because this port stubs out
					// DataFixerUpper (see net.minecraft.util.datafix.DataFixers), so it
					// will load with whatever the old chunk format leaves behind.
					CompoundTag version = new CompoundTag();
					version.putString("Name", "1.18.2");
					version.putInt("Id", 2975);
					version.putBoolean("Snapshot", false);
					worldDatNBT.getCompound("Data").put("Version", version);
					worldDatNBT.getCompound("Data").putInt("DataVersion", 2975);
					if(isWorld152) { //1.5.2 worlds should only be imported here
						EaglerLevelDat.initEaglerVersion(worldDatNBT.getCompound("Data"));
					}
					EaglerOutputStream bo = new EaglerOutputStream();
					NbtIo.writeCompressed(worldDatNBT, bo);
					b = bo.toByteArray();
					VFile2 ff = WorldsDB.newVFile(worldDir, fileName);
					ff.setAllBytes(b);
					prog += b.length;
				} else if ((fileName.endsWith(".mcr") || fileName.endsWith(".mca")) && (fileName.startsWith("region/") || fileName.startsWith("DIM1/region/") || fileName.startsWith("DIM-1/region/"))) {
					VFile2 chunkFolder = WorldsDB.newVFile(worldDir, fileName.startsWith("DIM1") ? "level1" : (fileName.startsWith("DIM-1") ? "level-1" : "level0"));
					String chunkFolderName = chunkFolder.getName();
					// 1.12.2 built a BiomeSource here to feed RegionFileStorage.convertToAnvilFormat,
					// its pre-Anvil (1.5.2) chunk upgrade. 1.18.2 removed that converter with the
					// format, so importing a 1.5.2 world is refused below rather than half-done.

					RegionFile mca = new RegionFile(new RandomAccessMemoryFile(b, b.length));
					int loadChunksCount = 0;
					for(int j = 0; j < 32; ++j) {
						for(int k = 0; k < 32; ++k) {
							if(!convertMap) {
								if(mca.isChunkSaved(j, k)) {
									CompoundTag chunkNBT;
									CompoundTag chunkLevel;
									try {
										chunkNBT = NbtIo.read(mca.getChunkDataInputStream(j, k));
										if(!chunkNBT.contains("Level", 10)) {
											throw new IOException("Chunk is missing level data!");
										}
										chunkLevel = chunkNBT.getCompound("Level");
									}catch(Throwable t) {
										logger.error("{}: Could not read chunk: {}, {}", fileName, j, k);
										logger.error(t);
										continue;
									}
									int chunkX = chunkLevel.getInt("xPos");
									int chunkZ = chunkLevel.getInt("zPos");
									VFile2 chunkOut = WorldsDB.newVFile(chunkFolder, EaglerChunkPath.getChunkPath(chunkX, chunkZ) + ".dat");
									if(chunkOut.exists()) {
										logger.error("{}: Chunk already exists: {}", fileName, chunkOut.getPath());
										continue;
									}
									EaglerOutputStream bao = new EaglerOutputStream();
									NbtIo.writeCompressed(chunkNBT, bao);
									b = bao.toByteArray();
									chunkOut.setAllBytes(b);
									prog += b.length;
									if (prog - lastProgUpdate > 25000) {
										lastProgUpdate = prog;
										EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.importing.2", prog);
									}
									++loadChunksCount;
								}
							} else {
								if(mca.isChunkSaved(j, k)) {
									CompoundTag nbttagcompound;
									CompoundTag chunkLevel;
									DataInputStream datainputstream = null;
									try {
										datainputstream = mca.getChunkDataInputStream(j, k);
									
										if (datainputstream == null) {
											logger.warn("Failed to fetch input stream");
											continue;
										}
									
										nbttagcompound = NbtIo.read(datainputstream);
										datainputstream.close();
										if(!nbttagcompound.contains("Level", 10)) {
											throw new IOException("Chunk is missing level data!");
										}
										chunkLevel = nbttagcompound.getCompound("Level");
									}catch(Throwable t) {
										if(datainputstream != null) {
											datainputstream.close();
										}
										logger.error("{}: Could not read chunk: {}, {}", fileName, j, k);
										logger.error(t);
										continue;
									}
									
									throw new IOException("This world is in the pre-Anvil (1.5.2) chunk format. "
											+ "Minecraft 1.18.2 no longer ships the converter for it, so it "
											+ "cannot be imported - open it once in an older version first.");
									/*
									CompoundTag nbttagcompound2 = new CompoundTag();
									CompoundTag nbttagcompound3 = new CompoundTag();
									nbttagcompound2.put("Level", nbttagcompound3);
									
									int chunkX = chunkLevel.getInt("xPos");
									int chunkZ = chunkLevel.getInt("zPos");
									VFile2 chunkOut = WorldsDB.newVFile(chunkFolder, EaglerChunkPath.getChunkPath(chunkX, chunkZ) + ".dat");
									if(chunkOut.exists()) {
										logger.error("{}: Chunk already exists: {}", fileName, chunkOut.getPath());
										continue;
									}
									
									EaglerOutputStream bao = new EaglerOutputStream();
									NbtIo.writeCompressed(nbttagcompound2, bao);
									b = bao.toByteArray();
									chunkOut.setAllBytes(b);
									prog += b.length;
									if (prog - lastProgUpdate > 25000) {
										lastProgUpdate = prog;
										EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.importing.2", prog);
									}
									++loadChunksCount;
									*/
								}
							}
						}
					}
					logger.info("{}: Imported {} chunks successfully ({} bytes)", fileName, loadChunksCount, prog);
				} else if (fileName.startsWith("playerdata/") || fileName.startsWith("stats/")) {
					//TODO: LAN player inventories
				} else if (fileName.startsWith("data/") || fileName.startsWith("players/") || fileName.startsWith("eagler/skulls/")) {
					VFile2 ff = WorldsDB.newVFile(worldDir, fileName);
					ff.setAllBytes(b);
					prog += b.length;
				} else if (!fileName.equals("level.dat_mcr") && !fileName.equals("session.lock")) {
					logger.info("Skipping file: {}", fileName);
				}
				if (prog - lastProgUpdate > 25000) {
					lastProgUpdate = prog;
					EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.importing.2", prog);
				}
			}
		}
		
		//workaround for another 1.5.2 bug
		VFile2 file = new VFile2(worldDir, "level.dat_old");
		if(!file.exists()) {
			VFile2 file2 = new VFile2(worldDir, "level.dat");
			file.setAllBytes(file2.getAllBytes());
		}
		
		logger.info("MCA was successfully extracted into directory \"{}\"", worldDir.getPath());
		String[] worldsTxt = FileUtils.worldsList.getAllLines();
		if(worldsTxt == null || worldsTxt.length <= 0 || (worldsTxt.length == 1 && worldsTxt[0].trim().length() <= 0)) {
			worldsTxt = new String[] { folderName };
		}else {
			String[] tmp = worldsTxt;
			worldsTxt = new String[worldsTxt.length + 1];
			System.arraycopy(tmp, 0, worldsTxt, 0, tmp.length);
			worldsTxt[worldsTxt.length - 1] = folderName;
		}
		FileUtils.worldsList.setAllChars(String.join("\n", worldsTxt));
	}

	public static byte[] exportWorld(String folderName) throws IOException {
		EaglerOutputStream bao = new EaglerOutputStream();
		VFile2 worldFolder;
		try(ZipOutputStream zos = new ZipOutputStream(bao)) {
			zos.setComment("contains backup of world '" + folderName + "'");
			worldFolder = new VFile2(EaglerIntegratedServerWorker.anvilConverter.createAccess(folderName).getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile().getPath());
			logger.info("Exporting world directory \"{}\" as MCA", worldFolder.getPath());
			VFile2 vf = WorldsDB.newVFile(worldFolder, "level.dat");
			byte[] b;
			int lastProgUpdate = 0;
			int prog = 0;
			boolean safe = false;
			if(vf.exists()) {
				zos.putNextEntry(new ZipEntry(folderName + "/level.dat"));
				b = vf.getAllBytes();
				zos.write(b);
				prog += b.length;
				safe = true;
			}
			vf = WorldsDB.newVFile(worldFolder, "level.dat_old");
			if(vf.exists()) {
				zos.putNextEntry(new ZipEntry(folderName + "/level.dat_old"));
				b = vf.getAllBytes();
				zos.write(b);
				prog += b.length;
				safe = true;
			}
			if (prog - lastProgUpdate > 25000) {
				lastProgUpdate = prog;
				EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.exporting.2", prog);
			}
			String[] srcFolderNames = new String[] { "level0", "level-1", "level1" };
			String[] dstFolderNames = new String[] { "/region/", "/DIM-1/region/", "/DIM1/region/" };
			List<VFile2> fileList;
			for(int i = 0; i < 3; ++i) {
				vf = WorldsDB.newVFile(worldFolder, srcFolderNames[i]);
				fileList = vf.listFiles(true);
				String regionFolder = folderName + dstFolderNames[i];
				logger.info("Converting chunks in \"{}\" as MCA to \"{}\"...", vf.getPath(), regionFolder);
				Map<String,RegionFile> regionFiles = new HashMap<>();
				for(int k = 0, l = fileList.size(); k < l; ++k) {
					VFile2 chunkFile = fileList.get(k);
					CompoundTag chunkNBT;
					CompoundTag chunkLevel;
					try {
						b = chunkFile.getAllBytes();
						chunkNBT = NbtIo.readCompressed(new EaglerInputStream(b));
						if(!chunkNBT.contains("Level", 10)) {
							throw new IOException("Chunk is missing level data!");
						}
						chunkLevel = chunkNBT.getCompound("Level");
					}catch(IOException t) {
						logger.error("Could not read chunk: {}", chunkFile.getPath());
						logger.error(t);
						continue;
					}
					int chunkX = chunkLevel.getInt("xPos");
					int chunkZ = chunkLevel.getInt("zPos");
					String regionFileName = "r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca";
					RegionFile rf = regionFiles.get(regionFileName);
					if(rf == null) {
						rf = new RegionFile(new RandomAccessMemoryFile(new byte[65536], 0));
						regionFiles.put(regionFileName, rf);
					}
					try(DataOutputStream dos = rf.getChunkDataOutputStream(chunkX & 31, chunkZ & 31)) {
						NbtIo.write(chunkNBT, dos);
					}catch(IOException t) {
						logger.error("Could not write chunk to {}: {}", regionFileName, chunkFile.getPath());
						logger.error(t);
						continue;
					}
					prog += b.length;
					if (prog - lastProgUpdate > 25000) {
						lastProgUpdate = prog;
						EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.exporting.2", prog);
					}
				}
				if(regionFiles.isEmpty()) {
					logger.info("No region files were generated");
					continue;
				}
				for(Entry<String,RegionFile> etr : regionFiles.entrySet()) {
					String regionPath = regionFolder + etr.getKey();
					logger.info("Writing region file: {}", regionPath);
					zos.putNextEntry(new ZipEntry(regionPath));
					zos.write(etr.getValue().getFile().getByteArray());
				}
			}
			logger.info("Copying extra world data...");
			fileList = WorldsDB.newVFile(worldFolder, "data").listFiles(false);
			for(int k = 0, l = fileList.size(); k < l; ++k) {
				VFile2 dataFile = fileList.get(k);
				zos.putNextEntry(new ZipEntry(folderName + "/data/" + dataFile.getName()));
				b = dataFile.getAllBytes();
				zos.write(b);
				prog += b.length;
				if (prog - lastProgUpdate > 25000) {
					lastProgUpdate = prog;
					EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.exporting.2", prog);
				}
			}
			fileList = WorldsDB.newVFile(worldFolder, "players").listFiles(false);
			for(int k = 0, l = fileList.size(); k < l; ++k) {
				VFile2 dataFile = fileList.get(k);
				zos.putNextEntry(new ZipEntry(folderName + "/players/" + dataFile.getName()));
				b = dataFile.getAllBytes();
				zos.write(b);
				prog += b.length;
				if (prog - lastProgUpdate > 25000) {
					lastProgUpdate = prog;
					EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.exporting.2", prog);
				}
			}
			fileList = WorldsDB.newVFile(worldFolder, "eagler/skulls").listFiles(false);
			for(int k = 0, l = fileList.size(); k < l; ++k) {
				VFile2 dataFile = fileList.get(k);
				zos.putNextEntry(new ZipEntry(folderName + "/eagler/skulls/" + dataFile.getName()));
				b = dataFile.getAllBytes();
				zos.write(b);
				prog += b.length;
				if (prog - lastProgUpdate > 25000) {
					lastProgUpdate = prog;
					EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.exporting.2", prog);
				}
			}
		}
		logger.info("World directory \"{}\" was successfully exported as MCA", worldFolder.getPath());
		return bao.toByteArray();
	}

}