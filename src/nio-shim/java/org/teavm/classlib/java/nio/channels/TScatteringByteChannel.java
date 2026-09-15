package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Missing from TeaVM's class library; see TFileChannel. */
public interface TScatteringByteChannel {

	long read(ByteBuffer[] dsts, int offset, int length) throws IOException;

	long read(ByteBuffer[] dsts) throws IOException;
}
