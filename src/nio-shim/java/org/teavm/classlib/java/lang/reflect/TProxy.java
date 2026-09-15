package org.teavm.classlib.java.lang.reflect;

import java.lang.reflect.InvocationHandler;

/**
 * Missing from TeaVM's class library. A dynamic proxy is generated at runtime from the
 * interface's reflective description, which erasure has already removed - TeaVM compiles
 * ahead of time and there is no class loader to define a new class into.
 */
public class TProxy {

	protected TProxy(InvocationHandler h) {
	}

	public static Object newProxyInstance(ClassLoader loader, Class<?>[] interfaces,
			InvocationHandler h) {
		throw new UnsupportedOperationException(
				"Dynamic proxies cannot be generated ahead of time under TeaVM");
	}

	public static boolean isProxyClass(Class<?> cl) {
		return false;
	}

	public static InvocationHandler getInvocationHandler(Object proxy) {
		throw new IllegalArgumentException("Not a proxy instance");
	}
}
