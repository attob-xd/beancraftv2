package org.teavm.classlib.java.util.concurrent;

/**
 * Missing from TeaVM's class library, and the first thing that stopped the built page from
 * even evaluating: guava's ThreadFactoryBuilder and three of Minecraft's lambdas
 * (Util.makeIoExecutor, ClientTelemetryManager, TextFilterClient) name it as a supertype,
 * so `var X = F(ThreadFactory)` ran at load time against an undefined symbol and took the
 * whole file down with a ReferenceError.
 *
 * That is worth remembering: a missing class is not only a problem when it is called. If
 * anything names it as a supertype or a field type, TeaVM emits that reference into the
 * class metadata, and the metadata is evaluated the moment the script loads.
 *
 * A page has one thread and cannot make more, so a factory here hands back a Thread object
 * that never starts anything; the executors it is given to run their work inline.
 */
public interface TThreadFactory {

	Thread newThread(Runnable r);
}
