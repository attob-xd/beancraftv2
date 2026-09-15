package net.minecraft.world.entity;

/**
 * Forces {@link Entity}'s synched-data accessors to be allocated before any subclass's.
 *
 * <p>{@code SynchedEntityData.defineId} numbers a class's accessors by looking up its nearest
 * initialised ancestor in a static pool and continuing from there, which silently assumes that
 * a superclass's static initialiser has already run when a subclass's does. That assumption is
 * a JVM class-initialisation guarantee; in this build it did not hold, and the symptom was a
 * hard crash the first time world generation placed a mineshaft:
 *
 * <pre>IllegalArgumentException: Duplicate id value for 0!
 *   at SynchedEntityData.define()
 *   at AbstractMinecart.defineSynchedData()</pre>
 *
 * <p>{@code AbstractMinecart}'s accessors had been numbered from 0 - the value the lookup
 * falls back to when it finds no initialised ancestor - and then {@code Entity}'s own
 * initialiser ran during the constructor and claimed 0 as well.
 *
 * <p>This class exists because {@code Entity}'s only non-constant static members are
 * {@code protected}, and every {@code public} one is a compile-time constant, which the
 * compiler inlines without triggering initialisation. Being in the same package, this can read
 * one of the protected fields, and reading it is what runs {@code Entity.<clinit>}.
 *
 * <p>{@code SynchedEntityData.defineId} calls this only when it is about to number a subclass
 * and {@code Entity} is not yet in the pool, so on a correctly-ordered run it never fires.
 */
public final class EntityDataBootstrap {

	/**
	 * Reads one of {@link Entity}'s accessors, which initialises the class and registers all
	 * of its accessors in the pool. The value is deliberately unused - touching the field is
	 * the entire point, and it must be a field the compiler cannot fold into a constant.
	 */
	public static void ensureEntityAccessorsAllocated() {
		if (Entity.DATA_SHARED_FLAGS_ID == null) {
			// Unreachable: a static final initialised by defineId is never null. The branch
			// exists so the read cannot be optimised away as having no effect.
			throw new IllegalStateException("Entity's synched data accessors failed to initialise");
		}
	}

	private EntityDataBootstrap() {
	}
}
