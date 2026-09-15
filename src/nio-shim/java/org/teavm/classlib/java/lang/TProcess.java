package org.teavm.classlib.java.lang;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Missing from TeaVM's class library. A page cannot start a process; vanilla names the type
 * where it would open a file manager or a browser for the user. EaglercraftX opens links
 * through the page itself.
 */
public abstract class TProcess {

	protected TProcess() {
	}

	public abstract OutputStream getOutputStream();

	public abstract InputStream getInputStream();

	public abstract InputStream getErrorStream();

	public abstract int waitFor();

	public abstract int exitValue();

	public abstract void destroy();
}
