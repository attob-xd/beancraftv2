package org.teavm.classlib.java.lang.reflect;

import java.lang.reflect.Type;

/** See TParameterizedType. */
public interface TWildcardType extends Type {

	Type[] getUpperBounds();

	Type[] getLowerBounds();
}
