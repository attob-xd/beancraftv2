package org.teavm.classlib.java.lang;

import java.util.function.Supplier;

/**
 * TeaVM's ThreadLocal is otherwise fine, but has no withInitial - the factory Minecraft
 * uses everywhere it wants a per-thread scratch value. There is one thread here, so a
 * "thread local" is simply a value; the initialiser still has to run lazily, exactly once,
 * because callers rely on that.
 */
public class TThreadLocal<T> {

	private boolean initialized;
	private T value;

	public TThreadLocal() {
	}

	protected T initialValue() {
		return null;
	}

	public T get() {
		if (!initialized) {
			value = initialValue();
			initialized = true;
		}
		return value;
	}

	public void set(T value) {
		this.value = value;
		initialized = true;
	}

	public void remove() {
		value = null;
		initialized = false;
	}

	public static <S> TThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
		return new TThreadLocal<S>() {
			@Override
			protected S initialValue() {
				return supplier.get();
			}
		};
	}
}
