package net.minecraft.client.main;

import java.io.File;
import java.net.Proxy;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.systems.RenderSystem;

import net.lax1dude.eaglercraft.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.cookie.ServerCookieDataStore;
import net.lax1dude.eaglercraft.profile.EaglerProfile;
import net.lax1dude.eaglercraft.profile.SkinPreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.SharedConstants;
import net.minecraft.client.User;
import net.minecraft.server.Bootstrap;

/**
 * EaglercraftX's browser entry point.
 *
 * <p>The desktop {@code main(String[])} parses command-line options and sets up a real
 * game directory; neither exists in a browser tab. {@code appMain()} builds the same
 * {@link GameConfig} directly and starts the client, as the 1.12.2 fork did — the shape of
 * that config is all that changed in 1.18.2.
 */
public class Main {

	private static final Logger LOGGER = LogUtils.getLogger();

	public static void appMain() {
		System.setProperty("java.net.preferIPv6Addresses", "true");

		// Offline user: the browser build never authenticates against Mojang. 1.12.2 built
		// this from a no-arg Session; 1.18.2's User needs the name, uuid and type up front.
		//
		// The uuid is not optional and cannot be blank. User.getProfileId() parses this
		// string, so "" makes it throw the moment anything asks who the player is - skin
		// lookups, the player list, the profile the integrated server registers. Vanilla
		// faces the same problem when launched without a session and answers it with
		// Player.createPlayerUUID(name), the standard offline-mode UUID derived from the
		// name, so the same name gives the same player across sessions and servers.
		// Computed here rather than through Player.createPlayerUUID, which is the same two
		// lines but drags Player's static initialiser - and through it LivingEntity's, and
		// every EntityDataAccessor those register - into the very first statement of the
		// client. Nothing needs entity classes loaded this early, and pulling them in ahead
		// of the rest of startup turned an unrelated failure in SynchedEntityData into a
		// crash before Minecraft was even constructed.
		String name = "Player";
		UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
		User user = new User(name, uuid.toString(), "",
				Optional.empty(), Optional.empty(), User.Type.LEGACY);

		GameConfig config = new GameConfig(
				new GameConfig.UserData(user, new PropertyMap(), new PropertyMap(), Proxy.NO_PROXY),
				// The canvas is resized by the page; these are the starting dimensions.
				new DisplayData(854, 480, OptionalInt.empty(), OptionalInt.empty(), false),
				// All four folders live in the browser VFS, which mounts them at these names.
				new GameConfig.FolderData(new File("."), new File("resourcepacks"), new File("assets"),
						null),
				new GameConfig.GameData(false, "1.18.2", "release", false, false),
				new GameConfig.ServerData(null, 0));

		PlatformRuntime.setThreadName("Client thread");

		// Vanilla's main() does this before it constructs Minecraft, and it is not optional:
		// MappedRegistry's constructor asks Bootstrap whether bootStrap() has run and throws
		// "Not bootstrapped" if not. Every registry initialises from a static initialiser, so
		// the first class to touch one - Heightmap, RuleTest, anything - takes the client
		// down. Rebuilding appMain() on 1.18.2's GameConfig had left these out.
		SharedConstants.tryDetectVersion();

		// Vanilla checks, for every entity and block-entity type it registers, that the
		// DataFixerUpper schema knows about it. This build has no schemas - see
		// net.minecraft.util.datafix.DataFixers for why the whole chain is left out - so
		// there is nothing to check against and asking for a schema throws.
		//
		// This is vanilla's own switch for skipping that validation, an ordinary mutable
		// field, and clearing it makes Util.fetchChoiceType return before it reaches the
		// fixer at all. Without it, Bootstrap logs "No data fixer registered for ..." once
		// per type - hundreds of lines - on the way to the same answer.
		SharedConstants.CHECK_DATA_FIXER_SCHEMA = false;

		Bootstrap.bootStrap();
		Bootstrap.validate();

		// Vanilla also calls Util.startTimerHackThread() here, which parks a daemon thread
		// forever so Windows keeps its high-resolution timer. There is one thread in a page
		// and nothing to keep awake, so it is left out deliberately.

		// RenderSystem has to be told which thread is which before anything touches it, and
		// this is the only place that can do it: vanilla does it in main(), and this method
		// replaces main().
		//
		// initRenderThread() is the load-bearing one. isOnRenderThread() is a plain identity
		// check against a static field, so with that field null it answers false for every
		// thread, and every assertOnRenderThread() in blaze3d throws. begin/finishInitialization
		// are kept because they bracket the constructor the way vanilla's do - in 1.18.2
		// isInInitPhase() happens to return a constant true, so they are not currently
		// holding anything up, but that is an implementation detail of this version and not
		// something to build on.
		RenderSystem.initRenderThread();
		RenderSystem.beginInitialization();

		/*
		 * These three lines are checkpoints, not decoration.
		 *
		 * The client reached a state where it stopped logging entirely and sat at 100% CPU
		 * with memory creeping about 2 MB a minute - the shape of a tight spin, not of work.
		 * Nothing in the resource reload had logged, so it was somewhere between here and the
		 * game loop, and that is a very large stretch of vanilla code with no output in it.
		 *
		 * Measurement ruled out the obvious suspects rather than guessing at them: the
		 * replaced GlslPreprocessor processes all 147 shaders on a JVM in 11 ms
		 * (tools/VerifyGlslPreprocessor.java), TeaVM's own regex engine agrees with the JDK on
		 * every rewritten pattern (tools/ProbeTeavmRegex.java), and parsing all 3,860 model and
		 * blockstate JSONs takes 12 ms (tools/TimeModelJson.java) - so "ModelBakery is just
		 * slow" cannot explain ten minutes.
		 *
		 * A 20-minute build is too expensive to spend on a guess, so these split the unknown
		 * in half: whether the constructor returns at all, and whether run() is entered. Keep
		 * them. They cost one log line each and they are the only output in this stretch.
		 */
		LOGGER.info("Constructing Minecraft");
		Minecraft minecraft = new Minecraft(config);
		LOGGER.info("Minecraft constructed");
		RenderSystem.finishInitialization();

		/*
		 * EaglercraftX's player profile: the name, skin and cape this client presents.
		 *
		 * 1.12.2 did both of these from inside Minecraft's constructor. That class is not
		 * reproduced here - it is 2,768 decompiled lines whose locals lost their generics
		 * with the rest of the jar - so they happen immediately after it instead, which is
		 * the same point in the startup order: the texture manager and the entity model set
		 * both exist, and no screen has been shown yet.
		 *
		 * read() restores the profile saved in browser storage under "p" and registers a
		 * texture for every custom skin and cape it finds. Without it the client had no
		 * profile at all: the name stayed empty, custom skins and capes never came back
		 * across a reload, and the edit screen opened on an empty list. initialize() bakes
		 * the three humanoid meshes that screen previews the player with.
		 *
		 * Neither can move earlier. Both touch the texture manager, and the registration
		 * they do is what makes the ResourceLocations in EaglerProfile resolvable.
		 */
		EaglerProfile.read();
		SkinPreviewRenderer.initialize();

		// Session cookies EaglercraftX servers hand out, restored from browser storage.
		// 1.12.2 loaded these on the next line after the profile; the store was ported with
		// everything else but nothing ever called load(), so a saved session was written and
		// then never read back, and every reconnect looked like a first visit.
		ServerCookieDataStore.load();

		LOGGER.info("Profile loaded: {}", EaglerProfile.getName());

		// false = "the game does not run on a separate thread from rendering". Minecraft's
		// own renderOnThread() returns false on desktop too, so vanilla takes this same
		// branch; here it is not a choice, because the page has one thread for both.
		RenderSystem.initGameThread(false);

		LOGGER.info("Entering the game loop");
		minecraft.run();
		LOGGER.info("The game loop returned");
	}
}
