package org.teavm.classlib.jdk.jfr;

/** See TEvent. */
public final class TFlightRecorder {

	public static boolean isAvailable() {
		return false;
	}

	public static TFlightRecorder getFlightRecorder() {
		throw new IllegalStateException("Flight Recorder is a JVM feature; there is no JVM here");
	}
}
