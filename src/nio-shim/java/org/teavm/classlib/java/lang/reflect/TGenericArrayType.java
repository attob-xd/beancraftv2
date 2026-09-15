package org.teavm.classlib.java.lang.reflect;

import java.lang.reflect.Type;

/** See TParameterizedType. */
public interface TGenericArrayType extends Type {

	Type getGenericComponentType();
}
