package net.lax1dude.eaglercraft.profile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.lax1dude.eaglercraft.EagRuntime;
import net.lax1dude.eaglercraft.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.opengl.ImageData;

/**
 * Fork addition: launch-URL test harness.
 *
 * Driving a cape test by hand means typing a username and picking a file out of
 * a NATIVE file dialog, which cannot be automated. These two launch options let
 * a URL do both, so two clients can be started with different identities and
 * different HD capes without anyone touching the GUI:
 *
 *   http://localhost:8081/?server=ws://localhost:25565&user=test1&cape=/capes/a.png
 *
 * Purely a debug aid. With neither option set nothing here runs.
 */
public class AutoTestProfile {

	private static final Logger logger = LogManager.getLogger("AutoTestProfile");

	public static void apply() {
		try {
			String user = EagRuntime.getConfiguration().getAutoUsername();
			if(user != null && user.length() > 0) {
				EaglerProfile.setName(user);
				logger.info("auto username: {}", user);
			}

			final String capeURL = EagRuntime.getConfiguration().getAutoCapeURL();
			if(capeURL != null && capeURL.length() > 0) {
				logger.info("auto cape: downloading {}", capeURL);
				PlatformRuntime.downloadRemoteURIByteArray(capeURL, arr -> {
					if(arr == null) {
						logger.error("auto cape: download failed");
						return;
					}
					try {
						ImageData img = ImageData.loadImageFile(arr, HDCapeAnimation.sniffMime(arr));
						if(img == null) {
							logger.error("auto cape: could not decode {} bytes", Integer.valueOf(arr.length));
							return;
						}
						byte[] legacy = HDCapeComposer.composeLegacy(img, 0, 0, HDCapeComposer.SCALE_ONE);
						int slot = EaglerProfile.addCustomCapeHD("autotest", legacy, arr, 0, 0,
								HDCapeComposer.SCALE_ONE);
						if(slot >= 0) {
							EaglerProfile.presetCapeId = -1;
							EaglerProfile.customCapeId = slot;
							ServerCapeCache.needReloadClientCape = true;
							EaglerProfile.save();
							logger.info("auto cape: equipped {}x{} ({} bytes)", Integer.valueOf(img.width),
									Integer.valueOf(img.height), Integer.valueOf(arr.length));
						}
					}catch(Throwable t) {
						logger.error("auto cape: failed", t);
					}
				});
			}
		}catch(Throwable t) {
			logger.error("auto test profile failed", t);
		}
	}

}
