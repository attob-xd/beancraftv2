package net.minecraft.gametest.framework;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.server.level.ServerLevel;

/**
 * Replaces vanilla's GameTestRegistry, which discovers Mojang's in-game test suite by
 * reflection: it walks a class's declared methods, reads their annotations, and calls
 * Class.newInstance() to build the test object.
 *
 * That last call is why this file exists. TeaVM cannot know what a reflective
 * newInstance() will construct, so it models it as "any class on the classpath may be
 * instantiated" - and once every class is reachable, so is every lambda in the JDK, which
 * then answers every BiFunction call site in the program. One reflective call put Swing,
 * java2d, JNDI and the image codecs into a browser build, for the bulk of 8,175
 * diagnostics.
 *
 * Nothing is lost: the gametest framework only runs under the dedicated server's
 * `/test` commands with a developer's test classes on the classpath. There are none here,
 * so an empty registry is the truthful answer rather than a stub.
 */
public class GameTestRegistry {

	public static void register(Class<?> testClass) {
	}

	public static void register(Method testMethod) {
	}

	public static Collection<TestFunction> getTestFunctionsForClassName(String className) {
		return Collections.emptyList();
	}

	public static Collection<TestFunction> getAllTestFunctions() {
		return Collections.emptyList();
	}

	public static Collection<String> getAllTestClassNames() {
		return Collections.emptyList();
	}

	public static boolean isTestClass(String className) {
		return false;
	}

	public static Consumer<ServerLevel> getBeforeBatchFunction(String batchName) {
		return null;
	}

	public static Consumer<ServerLevel> getAfterBatchFunction(String batchName) {
		return null;
	}

	public static Optional<TestFunction> findTestFunction(String testName) {
		return Optional.empty();
	}

	public static TestFunction getTestFunction(String testName) {
		throw new IllegalArgumentException("Can't find the test function for " + testName);
	}

	public static Collection<TestFunction> getLastFailedTests() {
		return Collections.emptyList();
	}

	public static void rememberFailedTest(TestFunction testFunction) {
	}

	public static void forgetFailedTests() {
	}
}
