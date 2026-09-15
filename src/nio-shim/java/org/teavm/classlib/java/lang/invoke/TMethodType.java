package org.teavm.classlib.java.lang.invoke;

/**
 * Replaces TeaVM's MethodType, an empty stub; see TMethodHandles for why these exist and why
 * extending them is safe.
 *
 * A method type is only ever built here to be handed straight to Lookup.findStatic, which
 * refuses it, so the descriptor is carried for its toString and nothing else.
 */
public final class TMethodType {

	private final Class<?> returnType;
	private final Class<?>[] parameterTypes;

	private TMethodType(Class<?> returnType, Class<?>[] parameterTypes) {
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
	}

	public static TMethodType methodType(Class<?> returnType) {
		return new TMethodType(returnType, new Class<?>[0]);
	}

	public static TMethodType methodType(Class<?> returnType, Class<?> parameterType) {
		return new TMethodType(returnType, new Class<?>[] { parameterType });
	}

	public static TMethodType methodType(Class<?> returnType, Class<?>... parameterTypes) {
		return new TMethodType(returnType, parameterTypes);
	}

	public Class<?> returnType() {
		return returnType;
	}

	public int parameterCount() {
		return parameterTypes.length;
	}

	public Class<?> parameterType(int index) {
		return parameterTypes[index];
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("(");
		for (int i = 0; i < parameterTypes.length; ++i) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(parameterTypes[i] == null ? "?" : parameterTypes[i].getName());
		}
		return sb.append(')').append(returnType == null ? "?" : returnType.getName()).toString();
	}
}
