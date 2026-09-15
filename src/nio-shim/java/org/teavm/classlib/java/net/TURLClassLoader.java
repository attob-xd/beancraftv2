package org.teavm.classlib.java.net;

import java.net.URL;

/**
 * Missing from TeaVM's class library. Loading a class from a URL at runtime is the opposite
 * of what an ahead-of-time compiler does: every class in this page was decided when it was
 * built. Vanilla names the type where a mod loader would go.
 */
public class TURLClassLoader extends ClassLoader {

	public TURLClassLoader(URL[] urls) {
		throw new UnsupportedOperationException(
				"Classes cannot be loaded at runtime in a TeaVM-compiled page");
	}

	public TURLClassLoader(URL[] urls, ClassLoader parent) {
		this(urls);
	}

	public URL[] getURLs() {
		return new URL[0];
	}
}
