package net.minecraft.world;

/**
 * 1.12.2's world-storage exception, which 1.18.2 removed.
 *
 * <p>Minecraft dropped it when world saving moved to {@code LevelStorageAccess} and
 * started using plain {@link java.io.IOException}. EaglercraftX's save handler and chunk
 * loader still declare and throw it across several files, and it carries a distinct
 * meaning there ("the world is locked / cannot be written"), so it is kept rather than
 * collapsed into IOException.
 */
public class MinecraftException extends Exception {

	private static final long serialVersionUID = 1L;

	public MinecraftException(String message) {
		super(message);
	}

	public MinecraftException(String message, Throwable cause) {
		super(message, cause);
	}
}
