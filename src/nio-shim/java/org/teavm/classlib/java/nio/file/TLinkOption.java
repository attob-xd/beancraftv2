package org.teavm.classlib.java.nio.file;

/** No symbolic links exist in the browser's filesystem, so these are accepted and ignored. */
public enum TLinkOption implements TOpenOption, TCopyOption {
	NOFOLLOW_LINKS
}
