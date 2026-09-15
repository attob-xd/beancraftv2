import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks the generated accessor-count table against the ids vanilla actually produces.
 *
 * <p>Entity metadata ids travel on the wire - {@code SynchedEntityData.writeDataItem} writes
 * them as a byte - so they cannot merely be self-consistent. They have to be the numbers a real
 * 1.18.2 server expects, or every entity's metadata is misread by whatever this connects to.
 * That makes "it works in singleplayer" an inadequate test, since both ends would be wrong
 * together.
 *
 * <p>So this runs on a desktop JVM, where class initialisation IS ordered parent-first and
 * vanilla's own numbering is therefore correct by construction. For each class in the table it
 * initialises the class, reads the ids off its declared {@code EntityDataAccessor} fields by
 * reflection, and compares that set against {@code {base, base+1, ..., base+count-1}} computed
 * from {@code EntityDataAccessorCounts}. Sets rather than sequences, because field declaration
 * order is not something reflection promises and the ids within a class do not depend on it.
 *
 * <p>Run through {@code sh tools/verify_entity_data_ids.sh}, which supplies the classpath.
 */
public class VerifyEntityDataIds {

	public static void main(String[] args) throws Exception {
		Class<?> counts = Class.forName("net.minecraft.world.entity.EntityDataAccessorCounts");
		Class<?> accessorType = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");

		Field countsField = counts.getDeclaredField("COUNTS");
		countsField.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<Class<?>, Integer> table = (java.util.Map<Class<?>, Integer>) countsField.get(null);

		java.lang.reflect.Method baseIdFor = counts.getMethod("baseIdFor", Class.class);
		java.lang.reflect.Method getId = accessorType.getMethod("getId");

		// Registries have to exist before most entity classes will initialise, and the data
		// fixers Bootstrap builds need the version first - the same two calls, in the same
		// order, that net.minecraft.client.main.Main makes before anything else.
		Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
		Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);

		// Initialise every class in the table first, so that vanilla's own pool is fully
		// populated before any id is read back.
		List<Class<?>> classes = new ArrayList<>(table.keySet());
		classes.sort((a, b) -> a.getName().compareTo(b.getName()));
		List<String> uninitialised = new ArrayList<>();
		for (Class<?> c : classes) {
			try {
				Class.forName(c.getName(), true, c.getClassLoader());
			} catch (Throwable t) {
				// A class that will not initialise headlessly cannot be checked. Record it
				// rather than skipping quietly - coverage the test does not have is exactly
				// what a reader would otherwise assume it does.
				uninitialised.add(c.getName() + " (" + t.getClass().getSimpleName() + ")");
			}
		}

		int checked = 0;
		List<String> problems = new ArrayList<>();

		for (Class<?> c : classes) {
			int declared = table.get(c);
			int base = (Integer) baseIdFor.invoke(null, c);
			boolean readable = true;
			for (String name : uninitialised) {
				if (name.startsWith(c.getName() + " (")) {
					readable = false;
					break;
				}
			}
			if (!readable) {
				continue;
			}

			Set<Integer> expected = new TreeSet<>();
			for (int i = 0; i < declared; i++) {
				expected.add(base + i);
			}

			Set<Integer> actual = new TreeSet<>();
			for (Field f : c.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers()) || !accessorType.isAssignableFrom(f.getType())) {
					continue;
				}
				f.setAccessible(true);
				Object accessor = f.get(null);
				if (accessor != null) {
					actual.add((Integer) getId.invoke(accessor));
				}
			}

			++checked;
			if (!expected.equals(actual)) {
				problems.add("  " + c.getName() + ": table says " + expected + ", vanilla says " + actual);
			}
		}

		// Every id must also be unique across each class's whole ancestry, which is the property
		// that actually failed in the browser ("Duplicate id value for 16!").
		for (Class<?> c : classes) {
			Set<Integer> seen = new HashSet<>();
			for (Class<?> a = c; a != null; a = a.getSuperclass()) {
				Integer declared = table.get(a);
				if (declared == null) {
					continue;
				}
				int base = (Integer) baseIdFor.invoke(null, a);
				for (int i = 0; i < declared; i++) {
					if (!seen.add(base + i)) {
						problems.add("  " + c.getName() + ": id " + (base + i)
								+ " is claimed twice in its ancestry (at " + a.getName() + ")");
					}
				}
			}
		}

		System.out.println("checked " + checked + " of " + classes.size() + " classes against vanilla");
		if (!uninitialised.isEmpty()) {
			System.out.println(uninitialised.size() + " could not be initialised headlessly, so their");
			System.out.println("ids were not read back (the arithmetic check below still covers them):");
			for (String name : uninitialised) {
				System.out.println("  " + name);
			}
		}
		if (problems.isEmpty()) {
			System.out.println("all ids match vanilla, and none collide within an ancestry");
		} else {
			System.out.println(problems.size() + " PROBLEMS:");
			for (String s : problems) {
				System.out.println(s);
			}
			System.exit(1);
		}
	}
}
