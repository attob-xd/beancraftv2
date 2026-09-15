package net.minecraft.client;

/**
 * 1.12.2's world-conversion failure, which 1.18.2 removed.
 *
 * <p>Minecraft dropped it when the old Anvil converter went away. EaglercraftX still
 * converts imported worlds itself (see {@code WorldConverterMCA}) and declares this on
 * those paths, so it is kept rather than collapsed into a plain exception.
 */
public class AnvilConverterException extends Exception {

	private static final long serialVersionUID = 1L;

	public AnvilConverterException(String message) {
		super(message);
	}

	public AnvilConverterException(String message, Throwable cause) {
		super(message, cause);
	}
}
