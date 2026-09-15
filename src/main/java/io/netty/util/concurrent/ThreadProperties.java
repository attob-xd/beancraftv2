package io.netty.util.concurrent;

/**
 * Replaces netty's own ThreadProperties, which declares
 *
 *     Thread.State state();
 *
 * TeaVM's java.lang.Thread has no State enum, and TeaVM writes a method's return type into
 * class metadata that is evaluated when the page loads - so that one signature was a
 * ReferenceError before anything ran. Thread.State cannot be added without replacing
 * TeaVM's whole Thread class, which is the core of the async machinery this port depends on.
 *
 * Dropping state() is safe here: netty exposes it for diagnostics on its event-loop threads,
 * and this build has no event loops at all - see net.minecraft.network.Connection.
 */
public interface ThreadProperties {

	boolean isAlive();

	boolean isDaemon();

	boolean isInterrupted();

	int priority();

	String name();

	long id();

	StackTraceElement[] stackTrace();
}
