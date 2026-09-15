package org.fusesource.jansi;

/**
 * Jansi writes ANSI escape codes so a terminal can colour its output. No jar on this
 * classpath provides it - log4j-core did, and that is gone - but a Mojang class still names
 * it, and TeaVM evaluates class metadata when the page loads, so the type has to resolve.
 *
 * There is no terminal behind a browser tab. The builder accepts calls and produces the
 * plain text, so anything that did run would log without escape codes rather than with
 * literal garbage in the console.
 */
public class Ansi {

	private final StringBuilder text = new StringBuilder();

	public static Ansi ansi() {
		return new Ansi();
	}

	public Ansi a(String value) {
		text.append(value);
		return this;
	}

	public Ansi a(Object value) {
		text.append(value);
		return this;
	}

	public Ansi fg(Object color) {
		return this;
	}

	public Ansi bg(Object color) {
		return this;
	}

	public Ansi bold() {
		return this;
	}

	public Ansi reset() {
		return this;
	}

	public Ansi newline() {
		text.append('\n');
		return this;
	}

	@Override
	public String toString() {
		return text.toString();
	}
}
