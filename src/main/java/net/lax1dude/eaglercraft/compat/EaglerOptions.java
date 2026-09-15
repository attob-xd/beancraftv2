package net.lax1dude.eaglercraft.compat;

/**
 * The settings EaglercraftX added to Minecraft's options screen.
 *
 * <p>In the 1.12.2 fork these were extra fields on {@code GameSettings}. 1.18.2's
 * {@code Options} comes from the jar and cannot gain fields, and copying its 784
 * decompiled lines to add six booleans would pull that file's lost local generics into
 * the build for no benefit. The client has a single Options instance, so the added
 * settings are held here instead.
 *
 * <p>Persistence: EaglercraftX wrote these through {@code GameSettings.saveOptions()}.
 * They are NOT yet saved or loaded - see ORPHANED_OVERRIDES.txt and the port notes;
 * hooking them into the 1.18.2 options file is outstanding work.
 */
public class EaglerOptions {

	/** Master switch for the deferred (shaders) pipeline. */
	public static boolean shaders = false;

	/** The live deferred-pipeline configuration. */
	public static net.lax1dude.eaglercraft.opengl.ext.deferred.EaglerDeferredConfig deferredShaderConf = new net.lax1dude.eaglercraft.opengl.ext.deferred.EaglerDeferredConfig();

	/** Whether the FNAW ("five nights") skin models are offered. */
	public static boolean enableFNAWSkins = true;

	public static boolean hideDefaultUsernameWarning = false;

	public static boolean hasHiddenPhishWarning = false;

	/** Set once the VFS world list has been migrated to the 1.18.2 layout. */
	public static boolean hasWorldListBeenConverted = false;

	/** Whether the server currently permits the FNAW skin models. */
	public static boolean currentFNAWSkinAllowedState = true;

	/** Whether the server is forcing them on. */
	public static boolean currentFNAWSkinForcedState = false;

	private EaglerOptions() {
	}
}
