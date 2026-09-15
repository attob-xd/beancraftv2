package org.teavm.classlib.java.lang.reflect;

import java.lang.reflect.Type;

/**
 * TeaVM's class library has Type, Method, Field and Constructor, but none of the generic
 * type interfaces. gson's $Gson$Types and guava's TypeToken both walk them, so without
 * these the two libraries cannot even be linked.
 *
 * They are declared, not implemented: TeaVM erases generics, so nothing in a compiled page
 * ever produces a ParameterizedType. What this buys is that the code which *checks* for
 * one - "is this a parameterized type? no, then treat it as a raw class" - links and takes
 * the raw path, which is the right answer here.
 */
public interface TParameterizedType extends Type {

	Type[] getActualTypeArguments();

	Type getRawType();

	Type getOwnerType();
}
