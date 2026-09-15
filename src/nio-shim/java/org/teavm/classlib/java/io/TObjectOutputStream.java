package org.teavm.classlib.java.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Missing from TeaVM's class library, and named in 73 places - almost all of them
 * java.io.Serializable's writeObject/readObject pair on exception and collection classes,
 * which the compiler emits whether or not anything serialises.
 *
 * Java serialisation genuinely cannot work under TeaVM: it is built on reflection over
 * field layouts that erasure has already removed. Nothing in Minecraft serialises this way
 * - saves are NBT, network is the packet protocol - so the methods refuse rather than
 * writing something that could never be read back.
 */
public class TObjectOutputStream {

	public TObjectOutputStream(OutputStream out) throws IOException {
		throw new IOException("Java serialisation is not available under TeaVM; use NBT");
	}

	public void writeObject(Object obj) throws IOException {
		throw new IOException("Java serialisation is not available under TeaVM");
	}

	public void defaultWriteObject() throws IOException {
		throw new IOException("Java serialisation is not available under TeaVM");
	}

	public void close() {
	}

	public void flush() {
	}
}
