package org.teavm.classlib.java.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Missing from TeaVM's class library, and it matters more than the reference count suggests:
 * EaglercraftX's compat layer keeps its side-car state in WeakHashMaps - the skin and cape
 * caches on EaglerClientState, the per-player brand UUIDs on EaglerServerState - because
 * those hang off jar-supplied classes that have nowhere to put a new field.
 *
 * There is no weakness here. JavaScript has WeakMap, but it cannot be iterated or sized,
 * and these side-cars need both. So entries live until they are removed explicitly, which
 * means a key that goes away without being removed is retained.
 *
 * That is a leak, and a bounded one: the side-cars are keyed on connections and players,
 * both of which are removed on disconnect. Worth knowing before something starts keying one
 * of these on entities or chunks.
 */
public class TWeakHashMap<K, V> extends HashMap<K, V> {

	public TWeakHashMap() {
	}

	public TWeakHashMap(int initialCapacity) {
		super(initialCapacity);
	}

	public TWeakHashMap(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
	}

	public TWeakHashMap(Map<? extends K, ? extends V> m) {
		super(m);
	}
}
