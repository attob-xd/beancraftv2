/*
 * Copyright 2014 Alexey Andreev. Licensed under the Apache License, Version 2.0.
 *
 * A verbatim re-statement of TeaVM 0.9.2's org.teavm.jso.indexeddb.IDBIndex with one
 * change, in getKeyPath()/unwrapStringArray().
 *
 * Upstream declares
 *
 *     @JSBody(params = "obj", script = "return obj;")
 *     @JSByRef
 *     private static native String[] unwrapStringArray(JSObject obj);
 *
 * and @JSByRef is only legal on arrays of primitives. TeaVM validates that during
 * dependency analysis and fails the whole build with
 *
 *     Method ...unwrapStringArray... is marked with @JSByRef, but does not return
 *     valid array type
 *
 * the moment getKeyPath() becomes reachable. Nothing in EaglercraftX calls it; it is
 * reachable only because this port compiles with fastGlobalAnalysis (1.18.2's class
 * graph is too large for the precise analysis to fit in memory), and that analysis
 * over-approximates virtual call targets. Copying the JSArray element by element does
 * the same job without the annotation. Source on the classpath wins over the jar, so
 * this file replaces upstream's.
 */
package org.teavm.jso.indexeddb;

import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSString;

public abstract class IDBIndex implements JSObject, IDBCursorSource {
	@JSProperty
	public abstract String getName();

	@JSProperty("keyPath")
	abstract JSObject getKeyPathImpl();

	public final String[] getKeyPath() {
		JSObject result = getKeyPathImpl();
		if (JSString.isInstance(result)) {
			return new String[] { ((JSString) result.cast()).stringValue() };
		}
		JSArray<JSString> array = result.cast();
		String[] strings = new String[array.getLength()];
		for (int i = 0; i < strings.length; ++i) {
			strings[i] = array.get(i).stringValue();
		}
		return strings;
	}

	@JSProperty
	public abstract boolean isMultiEntry();

	@JSProperty
	public abstract boolean isUnique();

	public abstract IDBCursorRequest openCursor();

	public abstract IDBCursorRequest openCursor(IDBKeyRange range);

	public abstract IDBCursorRequest openKeyCursor();

	public abstract IDBGetRequest get(JSObject key);

	public abstract IDBGetRequest getKey(JSObject key);

	public abstract IDBCountRequest count(JSObject key);

	public abstract IDBCountRequest count();
}
