package org.teavm.classlib.java.nio.channels;

import java.io.IOException;
import java.nio.ByteBuffer;

/** Missing from TeaVM's class library; see TFileChannel. */
public interface TGatheringByteChannel {

	long write(ByteBuffer[] srcs, int offset, int length) throws IOException;

	long write(ByteBuffer[] srcs) throws IOException;
}
