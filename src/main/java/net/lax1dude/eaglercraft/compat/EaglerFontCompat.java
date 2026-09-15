package net.lax1dude.eaglercraft.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.FormattedCharSequence;

/**
 * 1.12.2 font helpers that call sites reach through a plain {@link Font}.
 *
 * <p>{@code Minecraft.font} is typed {@code Font}, so an extra method on the port's
 * {@code FontRenderer} subclass would never resolve at those call sites even though the
 * instance is one. These are static instead.
 */
public class EaglerFontCompat {

	/** 1.12.2's {@code listFormattedStringToWidth}; 1.18.2 spells it split(). */
	public static List<String> listFormattedStringToWidth(Font font, String text, int wrapWidth) {
		List<String> out = new ArrayList<>();
		for (FormattedCharSequence seq : font.split(new TextComponent(text), wrapWidth)) {
			StringBuilder sb = new StringBuilder();
			seq.accept((idx, style, cp) -> {
				sb.appendCodePoint(cp);
				return true;
			});
			out.add(sb.toString());
		}
		return out;
	}

	private EaglerFontCompat() {
	}
}
