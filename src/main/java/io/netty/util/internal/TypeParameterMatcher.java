package io.netty.util.internal;

import java.util.Map;

/**
 * Netty's class, with the reflective type-parameter lookup removed.
 *
 * <p>Netty uses this to work out what message type a handler wants. {@code find} reads the
 * handler's own generic signature - {@code Connection extends SimpleChannelInboundHandler<Packet<?>>}
 * yields {@code Packet} - so that {@code acceptInboundMessage} can filter. It does that with
 * {@code Class.getGenericSuperclass()} and {@code Class.getTypeParameters()}, and TeaVM
 * implements neither: it keeps no generic signatures at runtime, so both throw
 * {@code NoSuchMethodError}.
 *
 * <p>That is not a corner of netty this build can avoid. {@code SimpleChannelInboundHandler}'s
 * constructor calls {@code find} as its first statement, and {@code MessageToByteEncoder} does
 * the same, so <b>constructing a {@code Connection} at all</b> - including the
 * {@code MemoryConnection} the integrated server is joined through - went straight into it.
 * Every route into a world, singleplayer and multiplayer alike, runs through that constructor.
 *
 * <p>The replacement does not invent a fallback; it takes the one netty already has.
 * {@code find0} returns {@code Object.class} whenever the generic supertype is not
 * parameterised or resolves to a variable it cannot place, and {@code get(Object.class)} is the
 * NOOP matcher that accepts everything. Reading no signature is exactly that situation, so
 * {@code find} answers with the NOOP matcher directly.
 *
 * <p>{@code get(Class)} is untouched and still exact, which matters more than it sounds: the
 * handlers that are given their type explicitly rather than through a generic parameter - the
 * {@code MessageToMessageEncoder(Class)} constructors - keep filtering precisely.
 *
 * <p><b>The difference this leaves.</b> A handler that would have matched only {@code Packet}
 * now accepts any inbound message, and {@code channelRead0} casts. In Minecraft's pipeline the
 * inbound side of that handler is fed by {@code PacketDecoder}, which produces nothing but
 * packets, so the set is the same in practice - but where vanilla would quietly pass an
 * unexpected message along, this build would throw {@code ClassCastException}. That is the
 * honest cost of not having the signature, and it fails loudly rather than silently.
 */
public abstract class TypeParameterMatcher {

	private static final TypeParameterMatcher NOOP = new TypeParameterMatcher() {
		@Override
		public boolean match(Object msg) {
			return true;
		}
	};

	TypeParameterMatcher() {
	}

	public abstract boolean match(Object msg);

	/** Netty's, unchanged - the type is known here, so the matcher stays exact. */
	public static TypeParameterMatcher get(Class<?> parameterType) {
		Map<Class<?>, TypeParameterMatcher> getCache = InternalThreadLocalMap.get().typeParameterMatcherGetCache();
		TypeParameterMatcher matcher = getCache.get(parameterType);
		if (matcher == null) {
			if (parameterType == Object.class) {
				matcher = NOOP;
			} else {
				matcher = new ReflectiveMatcher(parameterType);
			}

			getCache.put(parameterType, matcher);
		}

		return matcher;
	}

	/**
	 * Answers the NOOP matcher. See the class comment: the generic signature this would read
	 * does not exist at runtime, and netty's own answer for that is {@code Object.class}.
	 */
	public static TypeParameterMatcher find(Object object, Class<?> parametrizedSuperclass, String typeParamName) {
		return get(Object.class);
	}

	private static final class ReflectiveMatcher extends TypeParameterMatcher {
		private final Class<?> type;

		ReflectiveMatcher(Class<?> type) {
			this.type = type;
		}

		@Override
		public boolean match(Object msg) {
			return this.type.isInstance(msg);
		}
	}
}
