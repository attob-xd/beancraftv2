package net.minecraft.client.multiplayer.resolver;

/**
 * Vanilla's server-address check, with the {@code ServiceLoader} lookup taken out.
 *
 * <p>Vanilla builds a list of blocked-address predicates by asking {@code ServiceLoader} for
 * every {@code com.mojang.blocklist.BlockListSupplier} on the classpath:
 *
 * <pre>    final ImmutableList var0 = Streams.stream(ServiceLoader.load(BlockListSupplier.class))
 *        .map(BlockListSupplier::createBlockList)
 *        .filter(Objects::nonNull)
 *        .collect(ImmutableList.toImmutableList());</pre>
 *
 * <p>TeaVM has no service loading - there is no jar to scan for {@code META-INF/services} at
 * runtime - and that chain yielded {@code null} rather than an empty list. Nothing checked,
 * because on a desktop it cannot be null, so the very next thing every connection attempt did
 * was call {@code var0.stream()} on it:
 *
 * <pre>    TypeError: Cannot read properties of null (reading 'kg2')
 *      at AddressCheck$1.isAllowed()
 *      at ServerNameResolver.resolveAddress()
 *      at ConnectScreen$1.run()</pre>
 *
 * <p>That is every multiplayer connection, before a socket is even opened - the address is
 * checked first - and it surfaced as "Failed to connect to the server" with a JavaScript type
 * error where the server's name should be.
 *
 * <p><b>Allowing everything is what vanilla does here, not a relaxation of it.</b> The list is
 * Mojang's blocked-server list, and it only ever has entries when a {@code BlockListSupplier}
 * service is present to supply them. No such provider is on this build's classpath, so even
 * with a working {@code ServiceLoader} the list would be empty and {@code noneMatch} would
 * return true for every address. EaglercraftX also has no Mojang authentication, which is the
 * thing that list exists to protect. So this returns the answer the empty list would have
 * given, without the lookup that cannot work.
 */
public interface AddressCheck {
	boolean isAllowed(ResolvedServerAddress var1);

	boolean isAllowed(ServerAddress var1);

	static AddressCheck createFromService() {
		return new AddressCheck() {
			@Override
			public boolean isAllowed(ResolvedServerAddress var1) {
				return true;
			}

			@Override
			public boolean isAllowed(ServerAddress var1) {
				return true;
			}
		};
	}
}
