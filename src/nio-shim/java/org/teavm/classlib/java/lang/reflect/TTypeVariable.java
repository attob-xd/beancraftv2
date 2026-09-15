package org.teavm.classlib.java.lang.reflect;

import java.lang.reflect.Type;

/**
 * See TParameterizedType. The declaration bound (D extends GenericDeclaration) is dropped
 * because TeaVM has no GenericDeclaration; erasure is what linking depends on, and that is
 * unchanged.
 */
public interface TTypeVariable<D> extends Type {

	Type[] getBounds();

	D getGenericDeclaration();

	String getName();
}
