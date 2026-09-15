package org.teavm.classlib.jdk.jfr;

/**
 * Java Flight Recorder is a JVM feature; a page has no JVM to record. Minecraft's
 * net.minecraft.util.profiling.jfr events subclass this, and TeaVM writes a supertype into
 * class metadata that is evaluated at load time, so the name has to resolve. Nothing
 * records: see net.minecraft.util.profiling.jfr.JfrProfiler, whose isAvailable() is false.
 */
public abstract class TEvent {

	public void begin() {
	}

	public void end() {
	}

	public void commit() {
	}

	public boolean isEnabled() {
		return false;
	}

	public boolean shouldCommit() {
		return false;
	}
}
