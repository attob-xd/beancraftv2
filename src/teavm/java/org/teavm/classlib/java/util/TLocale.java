package org.teavm.classlib.java.util;

import java.util.Arrays;
import org.teavm.classlib.impl.unicode.CLDRHelper;
import org.teavm.classlib.java.io.TSerializable;
import org.teavm.classlib.java.lang.TCloneable;
import org.teavm.platform.metadata.ResourceArray;
import org.teavm.platform.metadata.ResourceMap;
import org.teavm.platform.metadata.StringResource;

/**
 * TeaVM's own java.util.Locale, with one method added: getISO3Country().
 *
 * Minecraft needs it. PeriodicNotificationManager is a resource-reload listener, and
 * Minecraft constructs one over assets/minecraft/regional_compliancies.json with
 * Minecraft::countryEqualsISO3 as its selector. That file is not empty - it carries the
 * Korean playtime notices under the key "KOR" - so apply() really does call the selector,
 * which really does call Locale.getDefault().getISO3Country(). Without the method that is a
 * NoSuchMethodError, and vanilla only guards the call against MissingResourceException, so
 * it is not caught: the first resource reload fails and the client never reaches a menu.
 *
 * Replacing the whole class is the mechanism TeaVM leaves for this - see
 * tools/trim_teavm_jso.sh, which drops the jar's copy of anything src/nio-shim also defines.
 * Everything below except getISO3Country and its table is TeaVM 0.9.2's class unchanged; it
 * carries no TeaVM annotations and no native methods, which is what made it safe to take
 * wholesale.
 *
 * The behaviour is the real one, not a stub for "KOR". ISO3_COUNTRIES was extracted from this
 * machine's JDK by calling getISO3Country() for every code in Locale.getISOCountries(), so a
 * browser reporting any country gets the answer the JDK would give, and a Korean client gets
 * the notices vanilla would show it. Unknown codes raise MissingResourceException exactly as
 * the JDK does, which is the case vanilla's catch block is written for.
 *
 * getISO3Language() is deliberately absent: nothing in the build calls it, and it would need
 * a second table of the same size to be equally honest.
 */
public final class TLocale implements TCloneable, TSerializable {
	private static TLocale defaultLocale;
	public static final TLocale CANADA = new TLocale("en", "CA");
	public static final TLocale CANADA_FRENCH = new TLocale("fr", "CA");
	public static final TLocale CHINA = new TLocale("zh", "CN");
	public static final TLocale CHINESE = new TLocale("zh", "");
	public static final TLocale ENGLISH = new TLocale("en", "");
	public static final TLocale FRANCE = new TLocale("fr", "FR");
	public static final TLocale FRENCH = new TLocale("fr", "");
	public static final TLocale GERMAN = new TLocale("de", "");
	public static final TLocale GERMANY = new TLocale("de", "DE");
	public static final TLocale ITALIAN = new TLocale("it", "");
	public static final TLocale ITALY = new TLocale("it", "IT");
	public static final TLocale JAPAN = new TLocale("ja", "JP");
	public static final TLocale JAPANESE = new TLocale("ja", "");
	public static final TLocale KOREA = new TLocale("ko", "KR");
	public static final TLocale KOREAN = new TLocale("ko", "");
	public static final TLocale PRC = new TLocale("zh", "CN");
	public static final TLocale SIMPLIFIED_CHINESE = new TLocale("zh", "CN");
	public static final TLocale TAIWAN = new TLocale("zh", "TW");
	public static final TLocale TRADITIONAL_CHINESE = new TLocale("zh", "TW");
	public static final TLocale UK = new TLocale("en", "GB");
	public static final TLocale US = new TLocale("en", "US");
	public static final TLocale ROOT = new TLocale("", "");
	private static TLocale[] availableLocales;

	/** ISO 3166-1 alpha-2 immediately followed by alpha-3, 249 records of 5 chars. */
	private static final String ISO3_COUNTRIES = ""
			+ "ADANDAEAREAFAFGAGATGAIAIAALALBAMARMAOAGOAQATAARARGASASMATAUTAUAUS"
			+ "AWABWAXALAAZAZEBABIHBBBRBBDBGDBEBELBFBFABGBGRBHBHRBIBDIBJBENBLBLM"
			+ "BMBMUBNBRNBOBOLBQBESBRBRABSBHSBTBTNBVBVTBWBWABYBLRBZBLZCACANCCCCK"
			+ "CDCODCFCAFCGCOGCHCHECICIVCKCOKCLCHLCMCMRCNCHNCOCOLCRCRICUCUBCVCPV"
			+ "CWCUWCXCXRCYCYPCZCZEDEDEUDJDJIDKDNKDMDMADODOMDZDZAECECUEEESTEGEGY"
			+ "EHESHERERIESESPETETHFIFINFJFJIFKFLKFMFSMFOFROFRFRAGAGABGBGBRGDGRD"
			+ "GEGEOGFGUFGGGGYGHGHAGIGIBGLGRLGMGMBGNGINGPGLPGQGNQGRGRCGSSGSGTGTM"
			+ "GUGUMGWGNBGYGUYHKHKGHMHMDHNHNDHRHRVHTHTIHUHUNIDIDNIEIRLILISRIMIMN"
			+ "ININDIOIOTIQIRQIRIRNISISLITITAJEJEYJMJAMJOJORJPJPNKEKENKGKGZKHKHM"
			+ "KIKIRKMCOMKNKNAKPPRKKRKORKWKWTKYCYMKZKAZLALAOLBLBNLCLCALILIELKLKA"
			+ "LRLBRLSLSOLTLTULULUXLVLVALYLBYMAMARMCMCOMDMDAMEMNEMFMAFMGMDGMHMHL"
			+ "MKMKDMLMLIMMMMRMNMNGMOMACMPMNPMQMTQMRMRTMSMSRMTMLTMUMUSMVMDVMWMWI"
			+ "MXMEXMYMYSMZMOZNANAMNCNCLNENERNFNFKNGNGANINICNLNLDNONORNPNPLNRNRU"
			+ "NUNIUNZNZLOMOMNPAPANPEPERPFPYFPGPNGPHPHLPKPAKPLPOLPMSPMPNPCNPRPRI"
			+ "PSPSEPTPRTPWPLWPYPRYQAQATREREUROROURSSRBRURUSRWRWASASAUSBSLBSCSYC"
			+ "SDSDNSESWESGSGPSHSHNSISVNSJSJMSKSVKSLSLESMSMRSNSENSOSOMSRSURSSSSD"
			+ "STSTPSVSLVSXSXMSYSYRSZSWZTCTCATDTCDTFATFTGTGOTHTHATJTJKTKTKLTLTLS"
			+ "TMTKMTNTUNTOTONTRTURTTTTOTVTUVTWTWNTZTZAUAUKRUGUGAUMUMIUSUSAUYURY"
			+ "UZUZBVAVATVCVCTVEVENVGVGBVIVIRVNVNMVUVUTWFWLFWSWSMYEYEMYTMYTZAZAF"
			+ "ZMZMBZWZWE";

	private transient String countryCode;
	private transient String languageCode;
	private transient String variantCode;

	public TLocale(String language) {
		this(language, "", "");
	}

	public TLocale(String language, String country) {
		this(language, country, "");
	}

	public TLocale(String language, String country, String variant) {
		if (language == null || country == null || variant == null) {
			throw new NullPointerException();
		} else if (language.length() == 0 && country.length() == 0) {
			this.languageCode = "";
			this.countryCode = "";
			this.variantCode = variant;
		} else {
			this.languageCode = language;
			this.countryCode = country;
			this.variantCode = variant;
		}
	}

	@Override
	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException var2) {
			return null;
		}
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		} else if (!(object instanceof TLocale)) {
			return false;
		} else {
			TLocale o = (TLocale)object;
			return this.languageCode.equals(o.languageCode) && this.countryCode.equals(o.countryCode) && this.variantCode.equals(o.variantCode);
		}
	}

	public static TLocale[] getAvailableLocales() {
		if (availableLocales == null) {
			ResourceArray<StringResource> strings = CLDRHelper.getAvailableLocales();
			availableLocales = new TLocale[strings.size()];

			for (int i = 0; i < strings.size(); i++) {
				String string = ((StringResource)strings.get(i)).getValue();
				int countryIndex = string.indexOf(45);
				if (countryIndex > 0) {
					availableLocales[i] = new TLocale(string.substring(0, countryIndex), string.substring(countryIndex + 1));
				} else {
					availableLocales[i] = new TLocale(string);
				}
			}
		}

		return Arrays.copyOf(availableLocales, availableLocales.length);
	}

	public String getCountry() {
		return this.countryCode;
	}

	public String getISO3Country() {
		if (this.countryCode.length() == 0) {
			return "";
		}
		if (this.countryCode.length() == 2) {
			for (int i = 0; i < ISO3_COUNTRIES.length(); i += 5) {
				if (ISO3_COUNTRIES.charAt(i) == this.countryCode.charAt(0)
						&& ISO3_COUNTRIES.charAt(i + 1) == this.countryCode.charAt(1)) {
					return ISO3_COUNTRIES.substring(i + 2, i + 5);
				}
			}
		}
		throw new TMissingResourceException(
				"Couldn't find 3-letter country code for " + this.countryCode,
				"FormatData_" + this.toString(), "ShortCountry");
	}

	public static TLocale getDefault() {
		return defaultLocale;
	}

	public String getDisplayCountry() {
		return this.getDisplayCountry(getDefault());
	}

	public String getDisplayCountry(TLocale locale) {
		String result = getDisplayCountry(locale.getLanguage() + "-" + locale.getCountry(), this.countryCode);
		if (result == null) {
			result = getDisplayCountry(locale.getLanguage(), this.countryCode);
		}

		return result != null ? result : this.countryCode;
	}

	private static String getDisplayCountry(String localeName, String country) {
		if (!CLDRHelper.getCountriesMap().has(localeName)) {
			return null;
		} else {
			ResourceMap<StringResource> countries = (ResourceMap<StringResource>)CLDRHelper.getCountriesMap().get(localeName);
			return !countries.has(country) ? null : ((StringResource)countries.get(country)).getValue();
		}
	}

	public String getDisplayLanguage() {
		return this.getDisplayLanguage(getDefault());
	}

	public String getDisplayLanguage(TLocale locale) {
		String result = getDisplayLanguage(locale.getLanguage() + "-" + locale.getCountry(), this.languageCode);
		if (result == null) {
			result = getDisplayLanguage(locale.getLanguage(), this.languageCode);
		}

		return result != null ? result : this.languageCode;
	}

	private static String getDisplayLanguage(String localeName, String language) {
		if (!CLDRHelper.getLanguagesMap().has(localeName)) {
			return null;
		} else {
			ResourceMap<StringResource> languages = (ResourceMap<StringResource>)CLDRHelper.getLanguagesMap().get(localeName);
			return !languages.has(language) ? null : ((StringResource)languages.get(language)).getValue();
		}
	}

	public String getDisplayName() {
		return this.getDisplayName(getDefault());
	}

	public String getDisplayName(TLocale locale) {
		int count = 0;
		StringBuilder buffer = new StringBuilder();
		if (this.languageCode.length() > 0) {
			buffer.append(this.getDisplayLanguage(locale));
			count++;
		}

		if (this.countryCode.length() > 0) {
			if (count == 1) {
				buffer.append(" (");
			}

			buffer.append(this.getDisplayCountry(locale));
			count++;
		}

		if (this.variantCode.length() > 0) {
			if (count == 1) {
				buffer.append(" (");
			} else if (count == 2) {
				buffer.append(",");
			}

			buffer.append(this.getDisplayVariant(locale));
			count++;
		}

		if (count > 1) {
			buffer.append(")");
		}

		return buffer.toString();
	}

	public String getDisplayVariant() {
		return this.getDisplayVariant(getDefault());
	}

	public String getDisplayVariant(TLocale locale) {
		return locale.getVariant();
	}

	public String getLanguage() {
		return this.languageCode;
	}

	public String getVariant() {
		return this.variantCode;
	}

	@Override
	public int hashCode() {
		return this.countryCode.hashCode() + this.languageCode.hashCode() + this.variantCode.hashCode();
	}

	public static void setDefault(TLocale locale) {
		if (locale != null) {
			defaultLocale = locale;
		} else {
			throw new NullPointerException();
		}
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		result.append(this.languageCode);
		if (this.countryCode.length() > 0) {
			result.append('_');
			result.append(this.countryCode);
		}

		if (this.variantCode.length() > 0 && result.length() > 0) {
			if (0 == this.countryCode.length()) {
				result.append("__");
			} else {
				result.append('_');
			}

			result.append(this.variantCode);
		}

		return result.toString();
	}

	static {
		String localeName = CLDRHelper.getDefaultLocale().getValue();
		int countryIndex = localeName.indexOf(95);
		defaultLocale = new TLocale(localeName.substring(0, countryIndex), localeName.substring(countryIndex + 1), "");
	}
}
