package net.lax1dude.eaglercraft.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Item predicates the high-poly renderer needs that 1.18.2 no longer exposes.
 */
public class EaglerItemCompat {

	/**
	 * 1.12.2's {@code Item.isFull3D()} - "rendered in full 3D when held", which picks the
	 * rod-style hand pose in {@link net.lax1dude.eaglercraft.profile.RenderHighPoly}.
	 *
	 * <p>The flag was dropped after 1.12.2. In the 1.12.2 fork exactly three items set it
	 * (stick, bone, blaze rod - the only three {@code setFull3D()} calls in Item.java), so
	 * the set is reproduced here rather than guessed at from item categories.
	 */
	public static boolean isFull3D(Item item) {
		return item == Items.STICK || item == Items.BONE || item == Items.BLAZE_ROD;
	}

	private EaglerItemCompat() {
	}
}
