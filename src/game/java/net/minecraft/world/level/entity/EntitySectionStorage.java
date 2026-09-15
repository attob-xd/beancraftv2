package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * Vanilla's class, with one method rewritten: {@link #getExistingSectionPositionsInChunk}.
 *
 * <p>Vanilla builds its {@code LongStream} with
 * {@code StreamSupport.longStream(Spliterators.spliteratorUnknownSize(iterator, ...), false)}.
 * TeaVM has no such thing - and this is not a missing overload that could be added. Its
 * {@code StreamSupport} is two methods over object streams, and there is no
 * spliterator-backed {@code LongStream} implementation anywhere in its class library; the only
 * long streams it can build are derived from an existing stream
 * ({@code TMappingToLongStreamImpl}, {@code TFlatMappingToLongStreamImpl}) or from an array
 * ({@code TArrayLongStreamImpl}).
 *
 * <p>So the positions are collected into a {@code long[]} and handed to
 * {@code Arrays.stream}, which is the array-backed implementation TeaVM does have. The set
 * being iterated is a {@code LongSortedSet} that already knows its own size, so this allocates
 * one correctly-sized array and copies into it - no growth, no boxing. The only behavioural
 * difference from vanilla is that the positions are read eagerly rather than lazily, which is
 * invisible here: both callers consume the whole stream immediately, and the set is not
 * modified while they do.
 *
 * <p>This is on the world-loading path - {@code PersistentEntitySectionManager} calls it for
 * every chunk it loads - so it is not an optional gap.
 */
public class EntitySectionStorage<T extends EntityAccess> {
	private final Class<T> entityClass;
	private final Long2ObjectFunction<Visibility> intialSectionVisibility;
	private final Long2ObjectMap<EntitySection<T>> sections = new Long2ObjectOpenHashMap<>();
	private final LongSortedSet sectionIds = new LongAVLTreeSet();

	public EntitySectionStorage(Class<T> var1, Long2ObjectFunction<Visibility> var2) {
		this.entityClass = var1;
		this.intialSectionVisibility = var2;
	}

	public void forEachAccessibleNonEmptySection(AABB var1, Consumer<EntitySection<T>> var2) {
		int var3 = SectionPos.posToSectionCoord(var1.minX - 2.0);
		int var4 = SectionPos.posToSectionCoord(var1.minY - 2.0);
		int var5 = SectionPos.posToSectionCoord(var1.minZ - 2.0);
		int var6 = SectionPos.posToSectionCoord(var1.maxX + 2.0);
		int var7 = SectionPos.posToSectionCoord(var1.maxY + 2.0);
		int var8 = SectionPos.posToSectionCoord(var1.maxZ + 2.0);

		for (int var9 = var3; var9 <= var6; var9++) {
			long var10 = SectionPos.asLong(var9, 0, 0);
			long var12 = SectionPos.asLong(var9, -1, -1);
			LongBidirectionalIterator var14 = this.sectionIds.subSet(var10, var12 + 1L).iterator();

			while (var14.hasNext()) {
				long var15 = var14.nextLong();
				int var17 = SectionPos.y(var15);
				int var18 = SectionPos.z(var15);
				if (var17 >= var4 && var17 <= var7 && var18 >= var5 && var18 <= var8) {
					EntitySection<T> var19 = this.sections.get(var15);
					if (var19 != null && !var19.isEmpty() && var19.getStatus().isAccessible()) {
						var2.accept(var19);
					}
				}
			}
		}
	}

	/** See the class comment: collected into an array because TeaVM cannot stream a spliterator of longs. */
	public LongStream getExistingSectionPositionsInChunk(long var1) {
		int var3 = ChunkPos.getX(var1);
		int var4 = ChunkPos.getZ(var1);
		LongSortedSet var5 = this.getChunkSections(var3, var4);
		if (var5.isEmpty()) {
			return LongStream.empty();
		}
		long[] var6 = new long[var5.size()];
		int var7 = 0;
		LongBidirectionalIterator var8 = var5.iterator();
		while (var8.hasNext()) {
			var6[var7++] = var8.nextLong();
		}
		// var7 rather than var6.length: size() and the iterator cannot disagree here, but
		// reading back only what was written is free and cannot be wrong.
		return Arrays.stream(var6, 0, var7);
	}

	private LongSortedSet getChunkSections(int var1, int var2) {
		long var3 = SectionPos.asLong(var1, 0, var2);
		long var5 = SectionPos.asLong(var1, -1, var2);
		return this.sectionIds.subSet(var3, var5 + 1L);
	}

	public Stream<EntitySection<T>> getExistingSectionsInChunk(long var1) {
		return this.getExistingSectionPositionsInChunk(var1)
				.<EntitySection<T>>mapToObj(this.sections::get)
				.filter(Objects::nonNull);
	}

	private static long getChunkKeyFromSectionKey(long var0) {
		return ChunkPos.asLong(SectionPos.x(var0), SectionPos.z(var0));
	}

	public EntitySection<T> getOrCreateSection(long var1) {
		return this.sections.computeIfAbsent(var1, this::createSection);
	}

	@Nullable
	public EntitySection<T> getSection(long var1) {
		return this.sections.get(var1);
	}

	private EntitySection<T> createSection(long var1) {
		long var3 = getChunkKeyFromSectionKey(var1);
		Visibility var5 = this.intialSectionVisibility.get(var3);
		this.sectionIds.add(var1);
		return new EntitySection<>(this.entityClass, var5);
	}

	public LongSet getAllChunksWithExistingSections() {
		LongOpenHashSet var1 = new LongOpenHashSet();
		this.sections.keySet().forEach(var1x -> var1.add(getChunkKeyFromSectionKey(var1x)));
		return var1;
	}

	public void getEntities(AABB var1, Consumer<T> var2) {
		this.forEachAccessibleNonEmptySection(var1, var2x -> var2x.getEntities(var1, var2));
	}

	public <U extends T> void getEntities(EntityTypeTest<T, U> var1, AABB var2, Consumer<U> var3) {
		this.forEachAccessibleNonEmptySection(var2, var3x -> var3x.getEntities(var1, var2, var3));
	}

	public void remove(long var1) {
		this.sections.remove(var1);
		this.sectionIds.remove(var1);
	}

	@VisibleForDebug
	public int count() {
		return this.sectionIds.size();
	}
}
