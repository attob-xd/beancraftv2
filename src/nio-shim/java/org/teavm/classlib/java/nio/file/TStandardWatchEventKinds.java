package org.teavm.classlib.java.nio.file;

public final class TStandardWatchEventKinds {

	private TStandardWatchEventKinds() {
	}

	private static final class StdKind<T> implements TWatchEvent.Kind<T> {
		private final String name;
		private final Class<T> type;

		StdKind(String name, Class<T> type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Class<T> type() {
			return type;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static final TWatchEvent.Kind<TPath> ENTRY_CREATE = new StdKind<>("ENTRY_CREATE", TPath.class);
	public static final TWatchEvent.Kind<TPath> ENTRY_DELETE = new StdKind<>("ENTRY_DELETE", TPath.class);
	public static final TWatchEvent.Kind<TPath> ENTRY_MODIFY = new StdKind<>("ENTRY_MODIFY", TPath.class);
	public static final TWatchEvent.Kind<Object> OVERFLOW = new StdKind<>("OVERFLOW", Object.class);
}
