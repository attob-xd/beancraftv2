package net.minecraft.util;

import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;

/**
 * Vanilla's class, with one line changed: the jar-signature check is gone.
 *
 * identify() asks Class.getSigners() whether the jar this class came from is still signed by
 * Mojang, and treats an unsigned jar as evidence the game has been modified. TeaVM has no
 * getSigners - there are no jars and no signatures in a page - so the call is a
 * NoSuchMethodError, and it is reached unconditionally: Minecraft's constructor calls
 * createTitle(), which calls checkModStatus(), which lands here. MinecraftServer does the
 * same on the singleplayer path, which is why replacing this class is the fix rather than
 * changing the client's brand string.
 *
 * What replaces it is the answer the check was reaching for. This build is assembled from a
 * decompiled and modified client, compiled to JavaScript; it is modified, and there is no
 * signature that could say otherwise. So the signature branch is replaced by VERY_LIKELY with
 * a description that says what is actually true. Nothing depends on this beyond the text in
 * crash reports and on the title screen, which is exactly where an honest answer belongs.
 *
 * The brand comparison above it is untouched and still runs first, so a client whose brand
 * really has changed is still reported that way.
 */
public final class ModCheck {

	private final Confidence confidence;
	private final String description;

	public ModCheck(Confidence confidence, String description) {
		this.confidence = confidence;
		this.description = description;
	}

	public static ModCheck identify(String expectedBrand, Supplier<String> brandSupplier,
			String side, Class<?> clazz) {
		String actualBrand = brandSupplier.get();
		if (!expectedBrand.equals(actualBrand)) {
			return new ModCheck(Confidence.DEFINITELY,
					side + " brand changed to '" + actualBrand + "'");
		}
		// Vanilla asks clazz.getSigners() here; see the class comment.
		return new ModCheck(Confidence.VERY_LIKELY, side + " jar signature invalidated");
	}

	public boolean shouldReportAsModified() {
		return this.confidence.shouldReportAsModified;
	}

	public ModCheck merge(ModCheck other) {
		return new ModCheck(ObjectUtils.max(this.confidence, other.confidence),
				this.description + "; " + other.description);
	}

	public String fullDescription() {
		return this.confidence.description + " " + this.description;
	}

	public Confidence confidence() {
		return this.confidence;
	}

	public String description() {
		return this.description;
	}

	@Override
	public String toString() {
		return "ModCheck[confidence=" + this.confidence + ", description=" + this.description + "]";
	}

	@Override
	public int hashCode() {
		return this.confidence.hashCode() * 31 + this.description.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ModCheck)) {
			return false;
		}
		ModCheck other = (ModCheck) o;
		return this.confidence == other.confidence && this.description.equals(other.description);
	}

	public static enum Confidence {
		PROBABLY_NOT("Probably not.", false),
		VERY_LIKELY("Very likely;", true),
		DEFINITELY("Definitely;", true);

		final String description;
		final boolean shouldReportAsModified;

		private Confidence(String description, boolean shouldReportAsModified) {
			this.description = description;
			this.shouldReportAsModified = shouldReportAsModified;
		}
	}
}
