package com.google.gson.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Replaces gson's Excluder, which decides what not to serialise.
 *
 * It is consulted for every adapter gson builds - it is the first factory in the chain - so
 * it is unavoidable for any use of gson at all, and Minecraft uses gson for options,
 * sounds.json, model and blockstate files, advancements and more.
 *
 * The one thing it does that TeaVM cannot is ask a Class whether it is anonymous or local.
 * Class.isAnonymousClass() and isLocalClass() need the InnerClasses attribute, which TeaVM
 * does not keep, so the call was a NoSuchMethodError raised during the first resource reload.
 *
 * That question is answerable without reflection. The JLS fixes how javac names these
 * classes: an anonymous class is Outer$1, a local class is Outer$1Name, and a member class is
 * Outer$Name. TeaVM does keep the real class name, so the name is read instead - see
 * isAnonymousName and isLocalName below. It is a naming convention rather than a declared
 * attribute, which is why it is written out explicitly here rather than hidden.
 *
 * One simplification, stated rather than smuggled: gson also asks whether such a class is
 * static, because a static nested class is not "local". javac never marks an anonymous class
 * static, and the modifier is not reliably available here either, so that test is dropped.
 * The effect is confined to gson's default rule of skipping anonymous and local classes when
 * serialising; nothing Minecraft serialises is one.
 */
public final class Excluder implements TypeAdapterFactory, Cloneable {

	private static final double IGNORE_VERSIONS = -1.0D;

	public static final Excluder DEFAULT = new Excluder();

	private double version = IGNORE_VERSIONS;
	private int modifiers = Modifier.TRANSIENT | Modifier.STATIC;
	private boolean serializeInnerClasses = true;
	private boolean requireExpose;
	private List<ExclusionStrategy> serializationStrategies = Collections.emptyList();
	private List<ExclusionStrategy> deserializationStrategies = Collections.emptyList();

	@Override
	protected Excluder clone() {
		try {
			return (Excluder) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new AssertionError(e);
		}
	}

	public Excluder withVersion(double ignoreVersionsAfter) {
		Excluder result = clone();
		result.version = ignoreVersionsAfter;
		return result;
	}

	public Excluder withModifiers(int... modifiers) {
		Excluder result = clone();
		result.modifiers = 0;
		for (int modifier : modifiers) {
			result.modifiers |= modifier;
		}
		return result;
	}

	public Excluder disableInnerClassSerialization() {
		Excluder result = clone();
		result.serializeInnerClasses = false;
		return result;
	}

	public Excluder excludeFieldsWithoutExposeAnnotation() {
		Excluder result = clone();
		result.requireExpose = true;
		return result;
	}

	public Excluder withExclusionStrategy(ExclusionStrategy exclusionStrategy,
			boolean serialization, boolean deserialization) {
		Excluder result = clone();
		if (serialization) {
			result.serializationStrategies = new ArrayList<>(this.serializationStrategies);
			result.serializationStrategies.add(exclusionStrategy);
		}
		if (deserialization) {
			result.deserializationStrategies = new ArrayList<>(this.deserializationStrategies);
			result.deserializationStrategies.add(exclusionStrategy);
		}
		return result;
	}

	/**
	 * Null means "no opinion, ask the next factory", which is the answer for everything this
	 * build serialises. The skipping adapter below is gson's own behaviour, kept so that an
	 * excluded type reads as null and writes as null rather than failing.
	 */
	@Override
	public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
		Class<?> rawType = type.getRawType();
		final boolean skipSerialize = excludeClass(rawType, true);
		final boolean skipDeserialize = excludeClass(rawType, false);
		if (!skipSerialize && !skipDeserialize) {
			return null;
		}
		return new TypeAdapter<T>() {
			private TypeAdapter<T> delegate;

			private TypeAdapter<T> delegate() {
				TypeAdapter<T> d = this.delegate;
				if (d == null) {
					d = gson.getDelegateAdapter(Excluder.this, type);
					this.delegate = d;
				}
				return d;
			}

			@Override
			public T read(JsonReader in) throws java.io.IOException {
				if (skipDeserialize) {
					in.skipValue();
					return null;
				}
				return delegate().read(in);
			}

			@Override
			public void write(JsonWriter out, T value) throws java.io.IOException {
				if (skipSerialize) {
					out.nullValue();
					return;
				}
				delegate().write(out, value);
			}
		};
	}

	public boolean excludeField(Field f, boolean serialize) {
		if ((this.modifiers & f.getModifiers()) != 0) {
			return true;
		}
		if (this.version != IGNORE_VERSIONS
				&& !isValidVersion(f.getAnnotation(Since.class), f.getAnnotation(Until.class))) {
			return true;
		}
		if (f.isSynthetic()) {
			return true;
		}
		if (this.requireExpose) {
			Expose annotation = f.getAnnotation(Expose.class);
			if (annotation == null || (serialize ? !annotation.serialize() : !annotation.deserialize())) {
				return true;
			}
		}
		if (!this.serializeInnerClasses && isInnerClass(f.getType())) {
			return true;
		}
		if (isAnonymousOrLocal(f.getType())) {
			return true;
		}
		List<ExclusionStrategy> list = serialize ? this.serializationStrategies
				: this.deserializationStrategies;
		if (!list.isEmpty()) {
			FieldAttributes fieldAttributes = new FieldAttributes(f);
			for (ExclusionStrategy exclusionStrategy : list) {
				if (exclusionStrategy.shouldSkipField(fieldAttributes)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean excludeClass(Class<?> clazz, boolean serialize) {
		if (this.version != IGNORE_VERSIONS
				&& !isValidVersion(clazz.getAnnotation(Since.class),
						clazz.getAnnotation(Until.class))) {
			return true;
		}
		if (!this.serializeInnerClasses && isInnerClass(clazz)) {
			return true;
		}
		if (isAnonymousOrLocal(clazz)) {
			return true;
		}
		List<ExclusionStrategy> list = serialize ? this.serializationStrategies
				: this.deserializationStrategies;
		for (ExclusionStrategy exclusionStrategy : list) {
			if (exclusionStrategy.shouldSkipClass(clazz)) {
				return true;
			}
		}
		return false;
	}

	/** See the class comment: decided by the JLS naming scheme, not by reflection. */
	private boolean isAnonymousOrLocal(Class<?> clazz) {
		if (Enum.class.isAssignableFrom(clazz)) {
			return false;
		}
		String binaryName = clazz.getName();
		int dollar = binaryName.lastIndexOf('$');
		if (dollar < 0 || dollar == binaryName.length() - 1) {
			return false;
		}
		// Outer$1 is anonymous; Outer$1Name is local; Outer$Name is a member class.
		return Character.isDigit(binaryName.charAt(dollar + 1));
	}

	private boolean isInnerClass(Class<?> clazz) {
		String binaryName = clazz.getName();
		int dollar = binaryName.lastIndexOf('$');
		return dollar >= 0 && dollar < binaryName.length() - 1
				&& !Character.isDigit(binaryName.charAt(dollar + 1));
	}

	private boolean isValidVersion(Since since, Until until) {
		return isValidSince(since) && isValidUntil(until);
	}

	private boolean isValidSince(Since annotation) {
		return annotation == null || annotation.value() <= this.version;
	}

	private boolean isValidUntil(Until annotation) {
		return annotation == null || annotation.value() > this.version;
	}
}
