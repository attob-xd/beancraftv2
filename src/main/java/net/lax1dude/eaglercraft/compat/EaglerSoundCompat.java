package net.lax1dude.eaglercraft.compat;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 1.12.2 played UI sounds by name; 1.18.2 wants a registered {@link SoundEvent}.
 *
 * <p>{@code PositionedSoundRecord.create(ResourceLocation, float)} took the sound's id
 * directly. Its 1.18.2 counterpart, {@code SimpleSoundInstance.forUI(SoundEvent, float)},
 * takes the registered event, so the id is resolved through the registry here.
 */
public class EaglerSoundCompat {

	/**
	 * The UI sound registered under {@code id}, or null when nothing is registered - the
	 * sound manager ignores a null instance, so an unknown id is silent rather than fatal.
	 */
	public static SimpleSoundInstance forUI(ResourceLocation id, float pitch) {
		SoundEvent event = Registry.SOUND_EVENT.get(id);
		return event == null ? null : SimpleSoundInstance.forUI(event, pitch);
	}

	private EaglerSoundCompat() {
	}
}
