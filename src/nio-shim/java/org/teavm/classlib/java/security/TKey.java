package org.teavm.classlib.java.security;

/**
 * Missing from TeaVM's class library. The JCA cannot work here at all - it loads its
 * providers reflectively, which is what forced Crypt to be replaced - but the key types are
 * named in metadata, so they have to resolve. Nothing constructs one: see net.minecraft.util.Crypt.
 */
public interface TKey {

	String getAlgorithm();

	String getFormat();

	byte[] getEncoded();
}
