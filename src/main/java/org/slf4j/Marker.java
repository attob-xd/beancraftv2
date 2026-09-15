package org.slf4j;

/**
 * Markers tag a log line so an appender can route it; there is one appender here (the
 * page console), so a marker is carried and printed but never routed on. See Logger.
 */
public interface Marker {

	String getName();

	void add(Marker reference);

	boolean remove(Marker reference);

	boolean contains(Marker other);

	boolean contains(String name);
}
