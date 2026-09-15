package org.teavm.classlib.java.lang.reflect;

/** See TParameterizedType; TeaVM erases generics, so nothing implements this. */
public interface TGenericDeclaration {

	java.lang.reflect.TypeVariable<?>[] getTypeParameters();
}
