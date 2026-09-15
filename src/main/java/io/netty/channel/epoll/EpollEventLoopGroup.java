package io.netty.channel.epoll;

/**
 * epoll is a Linux kernel interface. tools/trim_netty.sh drops the whole native transport
 * from the jar, but vanilla's Connection and ServerConnectionListener still name this type
 * in the LazyLoadedValue fields that would have held it - and TeaVM evaluates that metadata
 * at load time, so the name has to resolve.
 *
 * Nothing can construct one; the browser has no sockets at all.
 */
public class EpollEventLoopGroup {

	public EpollEventLoopGroup() {
		throw new UnsupportedOperationException("A browser tab has no epoll and no sockets");
	}

	public EpollEventLoopGroup(int nThreads) {
		this();
	}

	public EpollEventLoopGroup(int nThreads, java.util.concurrent.ThreadFactory threadFactory) {
		this();
	}
}
