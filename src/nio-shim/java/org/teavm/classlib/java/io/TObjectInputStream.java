package org.teavm.classlib.java.io;

import java.io.IOException;
import java.io.InputStream;

/** See TObjectOutputStream. */
public class TObjectInputStream {

	public TObjectInputStream(InputStream in) throws IOException {
		throw new IOException("Java serialisation is not available under TeaVM; use NBT");
	}

	public Object readObject() throws IOException {
		throw new IOException("Java serialisation is not available under TeaVM");
	}

	public void defaultReadObject() throws IOException {
		throw new IOException("Java serialisation is not available under TeaVM");
	}

	public void close() {
	}
}
