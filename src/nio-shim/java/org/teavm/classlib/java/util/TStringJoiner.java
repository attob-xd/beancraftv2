package org.teavm.classlib.java.util;

/** Missing from TeaVM's class library. */
public final class TStringJoiner {

	private final String separator;
	private final String prefix;
	private final String suffix;
	private StringBuilder value;
	private String emptyValue;

	public TStringJoiner(CharSequence separator) {
		this(separator, "", "");
	}

	public TStringJoiner(CharSequence separator, CharSequence prefix, CharSequence suffix) {
		this.separator = separator.toString();
		this.prefix = prefix.toString();
		this.suffix = suffix.toString();
		this.emptyValue = this.prefix + this.suffix;
	}

	public TStringJoiner setEmptyValue(CharSequence value) {
		emptyValue = value.toString();
		return this;
	}

	public TStringJoiner add(CharSequence element) {
		if (value == null) {
			value = new StringBuilder(prefix);
		} else {
			value.append(separator);
		}
		value.append(element);
		return this;
	}

	public TStringJoiner merge(TStringJoiner other) {
		if (other.value != null) {
			add(other.value.substring(other.prefix.length()));
		}
		return this;
	}

	public int length() {
		return value == null ? emptyValue.length() : value.length() + suffix.length();
	}

	@Override
	public String toString() {
		return value == null ? emptyValue : value.toString() + suffix;
	}
}
