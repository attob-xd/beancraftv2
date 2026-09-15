package net.minecraft.util.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

/**
 * Replaces vanilla's DataFixers, which builds Mojang's full DataFixerUpper schema chain -
 * every world format from 1.9 to 1.18.2 - in a static initialiser reached from
 * Minecraft's constructor.
 *
 * That chain is the single largest thing in the client by compiled size, and it is dead
 * weight in the browser: this build writes worlds at 1.18.2's own data version, so there
 * is never an older save to walk forward. EaglercraftX dropped the fixers on 1.12.2 for
 * the same reason.
 *
 * It is also what made the port impossible to compile at the time. DataFixerBuilder.addSchema
 * takes a BiFunction, and TeaVM's fast global analysis - which the build used then - resolved
 * that call site to every lambda of that shape on the classpath, including the JDK's own. One
 * of those reaches javax.naming, which reaches Class.newInstance, which TeaVM models as "any
 * class may be instantiated": Swing, java2d and the image codecs all became reachable, for
 * 8,809 diagnostics traceable to this one static initialiser. The build now runs the precise
 * analysis instead (fastGlobalAnalysis is false, for an unrelated reason - see build.gradle),
 * so that particular explosion would not recur; the size argument above is why this class
 * stays.
 *
 * The cost is that a world saved by an older Minecraft will not be upgraded on load.
 * Worlds created here are unaffected.
 */
public class DataFixers {

	private static final DataFixer DATA_FIXER = new DataFixer() {
		@Override
		public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version,
				int newVersion) {
			return input;
		}

		/**
		 * IllegalArgumentException specifically, because that is the one exception vanilla
		 * expects here. Util.doFetchChoiceType catches it, logs "No data fixer registered
		 * for {}" and carries on with a null type; anything else escapes and takes the
		 * client down. It did: an UnsupportedOperationException thrown from here killed the
		 * client during Bootstrap, once the boot path finally got far enough to reach it.
		 *
		 * That path should no longer be taken at all - Main clears
		 * SharedConstants.CHECK_DATA_FIXER_SCHEMA, which makes fetchChoiceType return
		 * before it asks for a schema - so this is the second line of defence, placed on
		 * the side that degrades rather than the side that crashes.
		 */
		@Override
		public Schema getSchema(int key) {
			throw new IllegalArgumentException(
					"EaglercraftX builds no DataFixerUpper schemas; see DataFixers");
		}
	};

	public static DataFixer getDataFixer() {
		return DATA_FIXER;
	}
}
