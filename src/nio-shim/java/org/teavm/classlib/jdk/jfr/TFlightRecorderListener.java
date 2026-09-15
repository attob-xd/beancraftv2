package org.teavm.classlib.jdk.jfr;

/** See TEvent; nothing ever records, so nothing is ever announced to a listener. */
public interface TFlightRecorderListener {

	default void recordingStateChanged(TRecording recording) {
	}

	default void recorderInitialized(TFlightRecorder recorder) {
	}
}
