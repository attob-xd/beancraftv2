package org.teavm.classlib.java.sql;

/**
 * Missing from TeaVM's class library. Nothing here talks to a database; the type is named
 * because gson registers a type adapter for it whenever a Gson instance is built, and that
 * registration is in metadata TeaVM evaluates at load time.
 */
public class TTime extends java.util.Date {

	public TTime(long time) {
		super(time);
	}
}
