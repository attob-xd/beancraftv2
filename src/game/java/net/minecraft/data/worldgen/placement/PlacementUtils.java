package net.minecraft.data.worldgen.placement;

import java.util.List;
import java.util.Random;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Vanilla's class, with one method changed: countExtra.
 *
 * TeaVM has no 32-bit float. Every Java float is a JavaScript number, which is a double, and
 * there is no fround anywhere in the emitted code - 0.9.2 does not implement one and offers
 * no switch for it. Arithmetic is therefore carried out at double precision and rounded only
 * when it reaches a double, which is normally harmless and often slightly more accurate.
 *
 * It is not harmless where vanilla asserts that a float lands exactly on an integer, and
 * countExtra does exactly that. It computes 1.0F / chance and rejects the result if it is not
 * within 1e-5 of a whole number. On a JVM, 1.0F / 0.1F rounds to exactly 10.0F and passes.
 * Here the same expression is 1.0 / 0.10000000149011612 = 9.999999850988388, which is 0.99
 * away from its own truncation - so the check threw "Chance data cannot be represented as
 * list weight" during Bootstrap, and the client died before reaching a menu.
 *
 * The fix is to round rather than to truncate: take the reciprocal in double, round to the
 * nearest integer, and reject only if it is genuinely not near one. That produces the same
 * weight a desktop JVM produces - 10 for a chance of 0.1 - so worldgen is unchanged. It also
 * fixes a second, quieter bug the same imprecision would have caused: vanilla derives the
 * list weight from (int) f, so without rounding the weight would have come out one too low
 * even in a build where the assertion had been removed.
 *
 * Everything else here is vanilla's, reproduced unchanged, because a class cannot be
 * partially replaced.
 */
public class PlacementUtils {

	public static final PlacementModifier HEIGHTMAP =
			HeightmapPlacement.onHeightmap(Heightmap.Types.MOTION_BLOCKING);
	public static final PlacementModifier HEIGHTMAP_TOP_SOLID =
			HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG);
	public static final PlacementModifier HEIGHTMAP_WORLD_SURFACE =
			HeightmapPlacement.onHeightmap(Heightmap.Types.WORLD_SURFACE_WG);
	public static final PlacementModifier HEIGHTMAP_OCEAN_FLOOR =
			HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR);
	public static final PlacementModifier FULL_RANGE =
			HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.top());
	public static final PlacementModifier RANGE_10_10 =
			HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(10), VerticalAnchor.belowTop(10));
	public static final PlacementModifier RANGE_8_8 =
			HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(8), VerticalAnchor.belowTop(8));
	public static final PlacementModifier RANGE_4_4 =
			HeightRangePlacement.uniform(VerticalAnchor.aboveBottom(4), VerticalAnchor.belowTop(4));
	public static final PlacementModifier RANGE_BOTTOM_TO_MAX_TERRAIN_HEIGHT =
			HeightRangePlacement.uniform(VerticalAnchor.bottom(), VerticalAnchor.absolute(256));

	/**
	 * Vanilla's way of forcing every placement class to initialise; the value returned is
	 * arbitrary and only the side effect matters.
	 */
	public static Holder<PlacedFeature> bootstrap() {
		List<Holder<PlacedFeature>> list = List.of(
				AquaticPlacements.KELP_COLD,
				CavePlacements.CAVE_VINES,
				EndPlacements.CHORUS_PLANT,
				MiscOverworldPlacements.BLUE_ICE,
				NetherPlacements.BASALT_BLOBS,
				OrePlacements.ORE_ANCIENT_DEBRIS_LARGE,
				TreePlacements.ACACIA_CHECKED,
				VegetationPlacements.BAMBOO_VEGETATION,
				VillagePlacements.PILE_HAY_VILLAGE);
		return Util.getRandom(list, new Random());
	}

	public static Holder<PlacedFeature> register(String name,
			Holder<? extends ConfiguredFeature<?, ?>> feature, List<PlacementModifier> modifiers) {
		return BuiltinRegistries.register(BuiltinRegistries.PLACED_FEATURE, name,
				new PlacedFeature(Holder.hackyErase(feature), List.copyOf(modifiers)));
	}

	public static Holder<PlacedFeature> register(String name,
			Holder<? extends ConfiguredFeature<?, ?>> feature, PlacementModifier... modifiers) {
		return register(name, feature, List.of(modifiers));
	}

	/** The one changed method; see the class comment. */
	public static PlacementModifier countExtra(int count, float chance, int extra) {
		double exact = 1.0D / chance;
		long rounded = Math.round(exact);
		if (rounded < 1L || Math.abs(exact - (double) rounded) > 1.0E-3D) {
			throw new IllegalStateException("Chance data cannot be represented as list weight");
		}
		int weight = (int) rounded;
		SimpleWeightedRandomList<IntProvider> list = SimpleWeightedRandomList.<IntProvider>builder()
				.add(ConstantInt.of(count), weight - 1)
				.add(ConstantInt.of(count + extra), 1)
				.build();
		return CountPlacement.of(new WeightedListInt(list));
	}

	public static PlacementFilter isEmpty() {
		return BlockPredicateFilter.forPredicate(BlockPredicate.matchesBlock(Blocks.AIR, BlockPos.ZERO));
	}

	public static BlockPredicateFilter filteredByBlockSurvival(Block block) {
		return BlockPredicateFilter.forPredicate(
				BlockPredicate.wouldSurvive(block.defaultBlockState(), BlockPos.ZERO));
	}

	public static Holder<PlacedFeature> inlinePlaced(
			Holder<? extends ConfiguredFeature<?, ?>> feature, PlacementModifier... modifiers) {
		return Holder.direct(new PlacedFeature(Holder.hackyErase(feature), List.of(modifiers)));
	}

	public static <FC extends FeatureConfiguration, F extends Feature<FC>> Holder<PlacedFeature>
			inlinePlaced(F feature, FC config, PlacementModifier... modifiers) {
		return inlinePlaced(Holder.direct(new ConfiguredFeature<>(feature, config)), modifiers);
	}

	public static <FC extends FeatureConfiguration, F extends Feature<FC>> Holder<PlacedFeature>
			onlyWhenEmpty(F feature, FC config) {
		return filtered(feature, config, BlockPredicate.matchesBlock(Blocks.AIR, BlockPos.ZERO));
	}

	public static <FC extends FeatureConfiguration, F extends Feature<FC>> Holder<PlacedFeature>
			filtered(F feature, FC config, BlockPredicate predicate) {
		return inlinePlaced(feature, config, BlockPredicateFilter.forPredicate(predicate));
	}
}
