package com.ibm.icu.text;

import java.util.Comparator;

/**
 * Replaces ICU's locale-aware string collator.
 *
 * ICU4J is a 12 MB library and this build vendors only the pieces vanilla actually needs
 * (see the rest of com.ibm.icu here). Collator has one caller: CreateBuffetWorldScreen sorts
 * the biome list with Collator.getInstance(), so that names order the way a reader of the
 * current language expects rather than by UTF-16 code unit.
 *
 * A real collator carries the Unicode collation tables and per-locale tailorings, which is
 * most of ICU's size. This one compares case-insensitively and then, for stability, exactly.
 * That is right for ASCII and close for most Latin text; it will order accented letters after
 * unaccented ones instead of alongside them, and it does not know that in Swedish "ä" sorts
 * after "z". The cost lands entirely on the display order of one list, which is why it is
 * worth taking rather than carrying the tables.
 */
public abstract class Collator implements Comparator<Object> {

	public static final int PRIMARY = 0;
	public static final int SECONDARY = 1;
	public static final int TERTIARY = 2;
	public static final int IDENTICAL = 3;

	private static final Collator INSTANCE = new Collator() {
		@Override
		public int compare(String left, String right) {
			int byCase = left.compareToIgnoreCase(right);
			// Fall back to the exact comparison so that strings differing only in case still
			// have a stable, total order - a sort needs one.
			return byCase != 0 ? byCase : left.compareTo(right);
		}
	};

	protected Collator() {
	}

	public static Collator getInstance() {
		return INSTANCE;
	}

	/**
	 * The locale is accepted and ignored - there are no per-locale tailorings here, which is
	 * the limitation described above. Vanilla calls this overload, not the no-arg one.
	 */
	public static Collator getInstance(java.util.Locale locale) {
		return INSTANCE;
	}

	public abstract int compare(String left, String right);

	@Override
	public int compare(Object left, Object right) {
		return compare(String.valueOf(left), String.valueOf(right));
	}

	public boolean equals(String left, String right) {
		return compare(left, right) == 0;
	}

	/** There are no collation tables to weaken or strengthen; see the class comment. */
	public void setStrength(int strength) {
	}

	public int getStrength() {
		return TERTIARY;
	}
}
