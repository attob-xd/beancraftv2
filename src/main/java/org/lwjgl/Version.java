package org.lwjgl;

/**
 * Replaces LWJGL's version banner. There is no LWJGL in the page - every class under
 * org.lwjgl in this build is a replacement - and RenderSystem prints this string into the
 * log and the F3 screen, so it says that rather than naming a version that is not here.
 */
public final class Version {

	private Version() {
	}

	public static String getVersion() {
		return "no LWJGL (EaglercraftX browser runtime)";
	}
}
