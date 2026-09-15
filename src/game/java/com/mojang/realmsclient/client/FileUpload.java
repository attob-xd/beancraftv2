package com.mojang.realmsclient.client;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.UploadInfo;
import com.mojang.realmsclient.gui.screens.UploadResult;

import net.minecraft.client.User;

/**
 * Replaces Realms' world uploader, for the same reason as FileDownload - see that class for
 * the full account.
 *
 * In short: vanilla's version is built on Apache HttpClient and commons-compress, and TeaVM
 * writes the types of its fields and parameters into class metadata that the browser
 * evaluates at load time. Those types were seven of the nine symbols the emitted script
 * referenced but never defined, which stopped the page before it started. Realms itself is
 * unreachable from this client anyway: it needs a Mojang account, and EaglercraftX has none.
 *
 * The failure is reported through vanilla's own channel - an UploadResult with a status code
 * and a message - so RealmsUploadScreen displays it the way it would display a server-side
 * refusal, instead of waiting on an upload that will never progress.
 */
public class FileUpload {

	private static final Logger LOGGER = LogUtils.getLogger();

	private final File file;
	private final long worldId;
	private final int slotId;
	private final UploadInfo uploadInfo;
	private final String sessionId;
	private final String username;
	private final String clientVersion;
	private final UploadStatus uploadStatus;
	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private CompletableFuture<UploadResult> uploadTask;

	public FileUpload(File file, long worldId, int slotId, UploadInfo uploadInfo, User user,
			String clientVersion, UploadStatus uploadStatus) {
		this.file = file;
		this.worldId = worldId;
		this.slotId = slotId;
		this.uploadInfo = uploadInfo;
		this.sessionId = user.getSessionId();
		this.username = user.getName();
		this.clientVersion = clientVersion;
		this.uploadStatus = uploadStatus;
	}

	/**
	 * 501 Not Implemented, which is the truth and is a code RealmsUploadScreen already knows
	 * how to render - it shows the message rather than a bare status.
	 */
	public void upload(Consumer<UploadResult> callback) {
		if (uploadTask != null) {
			return;
		}
		LOGGER.error("Cannot upload a world to Realms: Realms is not available in a browser,"
				+ " and this client has no Mojang account to reach it with");
		UploadResult result = new UploadResult.Builder()
				.withStatusCode(501)
				.withErrorMessage("Realms is not available in a browser")
				.build();
		uploadTask = CompletableFuture.completedFuture(result);
		callback.accept(result);
	}

	public void cancel() {
		cancelled.set(true);
		if (uploadTask != null) {
			uploadTask.cancel(false);
			uploadTask = null;
		}
	}

	public boolean isFinished() {
		return uploadTask != null && uploadTask.isDone();
	}
}
