package org.teavm.classlib.java.lang;

/**
 * The Java module a class belongs to. TeaVM compiles everything into one program with no
 * module system at all, so there is exactly one of these and it is unnamed. gson and guava
 * ask for it when deciding whether reflection is permitted; an unnamed module is the
 * permissive answer, which matches how this build behaves.
 */
public final class TModule {

	TModule() {
	}

	public String getName() {
		return null;
	}

	public boolean isNamed() {
		return false;
	}

	public boolean isOpen(String packageName) {
		return true;
	}

	public boolean isExported(String packageName) {
		return true;
	}
}
