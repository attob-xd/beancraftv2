package org.teavm.classlib.java.lang.invoke;

/**
 * Replaces TeaVM's MethodHandles, which is an empty stub - it exists only so that
 * LambdaMetafactory's signatures resolve, because TeaVM compiles lambdas away rather than
 * building them at runtime. Nothing calls into it, which is why adding to it is safe.
 *
 * blaze3d's InputConstants does call it, in a static initialiser that runs as soon as
 * anything touches a key binding:
 *
 *     lookup().findStatic(GLFW.class, "glfwRawMouseMotionSupported", methodType(boolean.class))
 *
 * It is a feature probe - "does this LWJGL build know about raw mouse motion?" - and vanilla
 * wraps it in catch (NoSuchMethodException | NoSuchFieldException), falling back to "not
 * supported". Any *other* Throwable is caught separately and rethrown wrapped, which is
 * fatal. With no lookup() at all the call raised NoSuchMethodError, an Error and therefore a
 * Throwable, so it took the fatal branch.
 *
 * So this exists to fail in the way vanilla expects: the lookup succeeds, and the search for
 * a member throws NoSuchMethodException. That is also the truthful answer - this build's GLFW
 * is EaglercraftX's own (see src/main/java/org/lwjgl/glfw/GLFW) and really does not have
 * glfwRawMouseMotionSupported, because a browser exposes no such thing. Vanilla then sets
 * raw mouse motion unsupported, which is correct.
 *
 * Reflection is not implemented here beyond that. Returning a working handle would mean
 * implementing member lookup on TeaVM's metadata, which nothing in this build needs.
 */
public class TMethodHandles {

	public static Lookup lookup() {
		return new Lookup();
	}

	public static Lookup publicLookup() {
		return new Lookup();
	}

	/**
	 * Answers every search with "no such member". See the class comment for why that is both
	 * the honest answer and the one vanilla is written to handle.
	 */
	public static final class Lookup {

		public Lookup() {
		}

		public java.lang.invoke.MethodHandle findStatic(Class<?> owner, String name,
				java.lang.invoke.MethodType type)
				throws NoSuchMethodException {
			throw new NoSuchMethodException(describe(owner, name));
		}

		public java.lang.invoke.MethodHandle findVirtual(Class<?> owner, String name,
				java.lang.invoke.MethodType type)
				throws NoSuchMethodException {
			throw new NoSuchMethodException(describe(owner, name));
		}

		public java.lang.invoke.MethodHandle findStaticGetter(Class<?> owner, String name,
				Class<?> fieldType)
				throws NoSuchFieldException {
			throw new NoSuchFieldException(describe(owner, name));
		}

		public java.lang.invoke.MethodHandle findGetter(Class<?> owner, String name,
				Class<?> fieldType)
				throws NoSuchFieldException {
			throw new NoSuchFieldException(describe(owner, name));
		}

		private static String describe(Class<?> owner, String name) {
			return (owner == null ? "?" : owner.getName()) + "." + name
					+ " (this build does not implement reflective member lookup)";
		}
	}
}
