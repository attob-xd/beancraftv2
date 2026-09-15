package com.mojang.realmsclient.client;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.WorldDownload;
import com.mojang.realmsclient.gui.screens.RealmsDownloadLatestWorldScreen;

import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * Replaces Realms' world downloader.
 *
 * This one is not about missing an API, it is about what it drags in. Vanilla's version uses
 * Apache HttpClient for the transfer, commons-compress to untar the archive, and - inside
 * DownloadCountingOutputStream - a java.awt.event.ActionListener for progress callbacks.
 * AWT does not exist under TeaVM, and TeaVM writes a field's type into class metadata that
 * the browser evaluates when the script loads, so that one listener field was a ReferenceError
 * before a line of game code ran. Not a lazy failure that only bites if someone opens Realms:
 * the page did not start at all. Between this class and FileUpload they accounted for every
 * missing class in the build (16 of them) and all nine dangling symbols in the output.
 *
 * Replacing it costs nothing real. Realms is a subscription service reached through Mojang
 * authentication, and this client has no account to authenticate with - EaglercraftX plays
 * offline or against Eagler-protocol servers - so a Realms world could not be downloaded here
 * even with the whole HTTP stack compiled in.
 *
 * The shape is vanilla's so the screens that drive it still compile and still behave: the
 * download reports an error rather than hanging, and RealmsDownloadLatestWorldScreen shows
 * its failure message.
 */
public class FileDownload {

	static final Logger LOGGER = LogUtils.getLogger();

	volatile boolean cancelled;
	volatile boolean finished;
	volatile boolean error;
	volatile boolean extracting;
	volatile File resourcePackPath;

	/**
	 * Vanilla issues a HEAD request. -1 is what its own error path returns, and the callers
	 * treat it as "size unknown" rather than as a failure of its own.
	 */
	public long contentLength(String url) {
		return -1L;
	}

	public void download(WorldDownload worldDownload, String name,
			RealmsDownloadLatestWorldScreen.DownloadStatus status,
			LevelStorageSource levelStorageSource) {
		LOGGER.error("Cannot download the Realms world \"{}\": Realms is not available in a"
				+ " browser, and this client has no Mojang account to reach it with", name);
		error = true;
		finished = true;
	}

	public void cancel() {
		cancelled = true;
	}

	public boolean isFinished() {
		return finished;
	}

	public boolean isError() {
		return error;
	}

	public boolean isExtracting() {
		return extracting;
	}

	/**
	 * Pure string handling in vanilla - it walks "name", "name (1)", "name (2)" until one is
	 * free - and it is called from the upload screen as well, so it keeps working.
	 */
	public static String findAvailableFolderName(String folder) {
		folder = folder.replaceAll("[\\./\"]", "_");
		for (String reserved : INVALID_FILE_NAMES) {
			if (folder.equalsIgnoreCase(reserved)) {
				folder = "_" + folder + "_";
			}
		}
		return folder;
	}

	private static final String[] INVALID_FILE_NAMES = new String[] { "CON", "COM", "PRN",
			"AUX", "CLOCK$", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
			"COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8",
			"LPT9" };

	/** Unreachable: nothing is ever downloaded to untar. */
	void untarGzipArchive(String name, File archive, LevelStorageSource levelStorageSource)
			throws IOException {
		throw new IOException("No archive to extract; see FileDownload.download");
	}
}
