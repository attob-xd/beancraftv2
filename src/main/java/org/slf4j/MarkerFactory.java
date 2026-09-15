package org.slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** See Marker. */
public final class MarkerFactory {

	private static final Map<String, Marker> MARKERS = new HashMap<>();

	private MarkerFactory() {
	}

	public static Marker getMarker(String name) {
		Marker m = MARKERS.get(name);
		if (m == null) {
			m = new BasicMarker(name);
			MARKERS.put(name, m);
		}
		return m;
	}

	public static Marker getDetachedMarker(String name) {
		return new BasicMarker(name);
	}

	private static final class BasicMarker implements Marker {
		private final String name;
		private final List<Marker> references = new ArrayList<>();

		BasicMarker(String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public void add(Marker reference) {
			if (reference != null && !references.contains(reference)) {
				references.add(reference);
			}
		}

		@Override
		public boolean remove(Marker reference) {
			return references.remove(reference);
		}

		@Override
		public boolean contains(Marker other) {
			if (equals(other)) {
				return true;
			}
			for (Marker m : references) {
				if (m.contains(other)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean contains(String other) {
			if (name.equals(other)) {
				return true;
			}
			for (Marker m : references) {
				if (m.contains(other)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
