package com.mojang.realmsclient.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Replaces Realms' texture manager. It downloads world-template images and player faces
 * over HTTP and decodes them with javax.imageio into a java.awt BufferedImage - none of
 * which exists under TeaVM, and all of which the dependency analysis then pulled in:
 * ImageIO's plugin lookup reaches Swing, java2d and the TIFF/BMP codecs.
 *
 * Realms is a subscription service reached through Mojang's own account system, which a
 * browser build cannot authenticate against; EaglercraftX has never shipped it. The
 * screens still exist in the jar, so this keeps their calls harmless rather than removing
 * the class.
 */
public class RealmsTextureManager {

	static final Map<String, Boolean> SKIN_FETCH_STATUS = new HashMap<>();
	static final Map<String, String> FETCHED_SKINS = new HashMap<>();
	static final Logger LOGGER = LogUtils.getLogger();

	public static void bindWorldTemplate(String templateId, String imageUrl) {
	}

	public static void withBoundFace(String uuid, Runnable runnable) {
		runnable.run();
	}
}
