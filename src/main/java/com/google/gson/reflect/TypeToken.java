package com.google.gson.reflect;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.internal.$Gson$Types;

/**
 * Replaces gson's TypeToken, which recovers a generic type argument by reflection.
 *
 * The idiom is {@code new TypeToken<Map<String, Foo>>(){}}: an anonymous subclass whose
 * generic superclass carries the type, read back with Class.getGenericSuperclass(). TeaVM
 * does not implement that, or getTypeParameters, or getGenericInterfaces - it keeps no
 * generic signatures at all - so the constructor raised NoSuchMethodError. It is on the boot
 * path twice over: Options and SoundManager both build one in a static initialiser, and
 * Minecraft's constructor touches both.
 *
 * The type genuinely cannot be recovered at run time, so it is supplied here instead. Every
 * class on this build's classpath that extends TypeToken was enumerated - there are exactly
 * three, all in vanilla - and each is listed below with the type its declaration names:
 *
 *     net.minecraft.client.Options$1             TypeToken&lt;List&lt;String&gt;&gt;
 *     net.minecraft.client.sounds.SoundManager$1 TypeToken&lt;Map&lt;String, SoundEventRegistration&gt;&gt;
 *     net.minecraft.server.PlayerAdvancements$1  TypeToken&lt;Map&lt;ResourceLocation, AdvancementProgress&gt;&gt;
 *
 * A table keyed by class name is not elegant, and the alternative was worse: the only other
 * way to avoid the reflection is to replace all three vanilla classes, one of which (Options)
 * is 31 KB of compiled code, to pass an explicit Type instead.
 *
 * An unlisted subclass throws rather than falling back to Object. Falling back would look
 * like it worked - gson would deserialise sounds.json into a map of LinkedTreeMap instead of
 * SoundEventRegistration, and the failure would surface later as a ClassCastException in the
 * resource reload, far from the cause. The exception below names the class and says what to
 * add. To re-derive the list, scan the classpath for classes whose superclass is
 * com/google/gson/reflect/TypeToken.
 *
 * Everything else is gson's own behaviour, kept so its adapter cache and type resolution work
 * unchanged: the same field names, the same equality by canonical type, the same factories.
 */
public class TypeToken<T> {

	/** Anonymous subclass name to the type its declaration names; see the class comment. */
	private static final Map<String, Type> DECLARED_TYPES = new HashMap<>();

	private static void declare(String subclass, Type type) {
		DECLARED_TYPES.put(subclass, type);
	}

	private static Type parameterized(Class<?> raw, Class<?>... args) {
		return $Gson$Types.newParameterizedTypeWithOwner(null, raw, (Type[]) args);
	}

	/*
	 * This table is complete for vanilla 1.18.2, and that is checked rather than hoped:
	 * scanning every entry in 1.18.2-mapped-trimmed.jar for a superclass of TypeToken finds
	 * exactly these three and nothing else, so no unlisted subclass can appear at runtime.
	 *
	 * Redo that scan if the game jar is ever re-mapped or replaced. Read each class's
	 * superclass out of its constant pool rather than grepping the decompiled tree - a
	 * decompiler does not render an anonymous subclass in a form grep can find, which is why
	 * the first two entries here were originally discovered by crashing instead.
	 *
	 * A resource pack cannot add one; these are Java classes, not data.
	 */
	static {
		// Declared as Collection<String>, not the List<String> vanilla wrote, on purpose.
		//
		// gson resolves a collection's element type with $Gson$Types.getSupertype(context,
		// rawType, Collection.class), whose first step is "if (toResolve == rawType) return
		// context". For a raw type of Collection that short-circuit answers immediately; for
		// List it falls through to rawType.getGenericInterfaces(), which TeaVM's Class does not
		// have - so the first Options.readPackList after an options.txt exists threw
		// NoSuchMethodError, and the menu only ever worked on a fresh profile. The Map tokens
		// below never hit this because their raw type already is Map.
		//
		// The difference is invisible to the caller: gson's ConstructorConstructor builds an
		// ArrayList for a plain Collection, and readPackList casts the result to List<String>.
		declare("net.minecraft.client.Options$1", parameterized(java.util.Collection.class, String.class));
		declare("net.minecraft.client.sounds.SoundManager$1", parameterized(Map.class,
				String.class, net.minecraft.client.resources.sounds.SoundEventRegistration.class));
		declare("net.minecraft.server.PlayerAdvancements$1", parameterized(Map.class,
				net.minecraft.resources.ResourceLocation.class,
				net.minecraft.advancements.AdvancementProgress.class));
	}

	final Class<? super T> rawType;
	final Type type;
	final int hashCode;

	@SuppressWarnings("unchecked")
	protected TypeToken() {
		this.type = declaredType(getClass());
		this.rawType = (Class<? super T>) $Gson$Types.getRawType(this.type);
		this.hashCode = this.type.hashCode();
	}

	@SuppressWarnings("unchecked")
	TypeToken(Type type) {
		this.type = $Gson$Types.canonicalize(type);
		this.rawType = (Class<? super T>) $Gson$Types.getRawType(this.type);
		this.hashCode = this.type.hashCode();
	}

	private static Type declaredType(Class<?> subclass) {
		Type declared = DECLARED_TYPES.get(subclass.getName());
		if (declared == null) {
			throw new UnsupportedOperationException(
					"TeaVM keeps no generic signatures, so the type argument of "
							+ subclass.getName() + " cannot be recovered at run time. Add it to"
							+ " the table in com.google.gson.reflect.TypeToken.");
		}
		return declared;
	}

	public final Class<? super T> getRawType() {
		return rawType;
	}

	public final Type getType() {
		return type;
	}

	/**
	 * gson's version walks the full generic hierarchy. Without generic signatures the only
	 * question that can be answered is about raw types, which is what its callers here ask.
	 */
	public boolean isAssignableFrom(Class<?> cls) {
		return rawType.isAssignableFrom(cls);
	}

	public boolean isAssignableFrom(Type from) {
		return from != null && rawType.isAssignableFrom($Gson$Types.getRawType(from));
	}

	public boolean isAssignableFrom(TypeToken<?> token) {
		return token != null && isAssignableFrom(token.getType());
	}

	@Override
	public final int hashCode() {
		return this.hashCode;
	}

	@Override
	public final boolean equals(Object o) {
		return o instanceof TypeToken<?> && $Gson$Types.equals(type, ((TypeToken<?>) o).type);
	}

	@Override
	public final String toString() {
		return $Gson$Types.typeToString(type);
	}

	public static TypeToken<?> get(Type type) {
		return new TypeToken<Object>(type);
	}

	public static <T> TypeToken<T> get(Class<T> type) {
		return new TypeToken<T>(type);
	}

	public static TypeToken<?> getParameterized(Type rawType, Type... typeArguments) {
		return new TypeToken<Object>(
				$Gson$Types.newParameterizedTypeWithOwner(null, rawType, typeArguments));
	}

	public static TypeToken<?> getArray(Type componentType) {
		return new TypeToken<Object>($Gson$Types.arrayOf(componentType));
	}
}
