package net.lax1dude.eaglercraft;

public class EaglercraftVersion {

	//////////////////////////////////////////////////////////////////////

	/// Customize these to fit your fork:

	// projectForkName feeds clientBrandUUID below, which is the identity this client reports
	// to a server, so renaming the fork changes that UUID. Nothing in this project stores it,
	// but a server that had allow-listed the old brand would see a new one.
	//
	// The URLs are lax1dude's GitLab rather than a repo for this fork, because there is not a
	// published one to point at - the "Fork on GitLab" button on the title screen opens this.
	// Replace both with your own repository when there is one.

	public static final String projectForkName = "EaglercraftX 1.18.2";
	public static final String projectForkVersion = "u2";
	public static final String projectForkVendor = "Ari";

	public static final String projectForkURL = "https://gitlab.com/lax1dude";

	//////////////////////////////////////////////////////////////////////

	public static final String projectOriginName = "EaglercraftX 1.18.2";
	public static final String projectOriginAuthor = "lax1dude";
	public static final String projectOriginVersion = "u2";

	public static final String projectOriginURL = "https://gitlab.com/lax1dude";

	// EPK Version Identifier

	public static final String EPKVersionIdentifier = null; // Set to null to disable EPK version check

	// Client brand identification system configuration

	public static final EaglercraftUUID clientBrandUUID = EagUtils.makeClientBrandUUID(projectForkName);

	public static final EaglercraftUUID legacyClientUUIDInSharedWorld = EagUtils
			.makeClientBrandUUIDLegacy(projectOriginName);

	// Miscellaneous variables:

	public static final String mainMenuStringA = "Minecraft 1.18.2";
	public static final String mainMenuStringB = projectOriginName + " " + projectOriginVersion;
	public static final String mainMenuStringC = "";
	public static final String mainMenuStringD = "Resources Copyright Mojang AB";

	public static final String mainMenuStringE = projectForkName + " " + projectForkVersion;
	public static final String mainMenuStringF = "Made by " + projectForkVendor;

	public static final long demoWorldSeed = (long) "North Carolina".hashCode();

	public static final boolean mainMenuEnableGithubButton = true;

	public static final boolean forceDemoMode = false;

	/*
	 * Deliberately a different namespace from the 1.12.2 fork's "_eaglercraft_1.12".
	 *
	 * This is the key everything persistent hangs off - saved worlds, options, servers,
	 * skins. Sharing it would put 1.12.2 worlds in front of a 1.18.2 client that cannot
	 * read them: the two save formats differ, and this port stubs out DataFixerUpper (see
	 * net.minecraft.util.datafix.DataFixers), so nothing would upgrade them. Separating the
	 * namespaces means the old worlds are still there, untouched, for the 1.12.2 build.
	 */
	public static final String localStorageNamespace = "_eaglercraft_1.18";

}
