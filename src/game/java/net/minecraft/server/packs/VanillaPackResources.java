package net.minecraft.server.packs;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.mojang.logging.LogUtils;

import net.lax1dude.eaglercraft.EagRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.server.packs.resources.SimpleResource;

/**
 * Replaces the built-in resource pack - the one holding everything under assets/ and data/
 * that ships with the game.
 *
 * Vanilla reads it out of its own jar through the class loader: getResourceAsStream for a
 * single file, and ClassLoader.getResources to enumerate a directory. Neither exists here.
 * TeaVM compiles ahead of time and there is no jar at runtime, so
 * ClassLoader.getResources is not implemented at all - the client died on it during
 * Minecraft's constructor with a NoSuchMethodError, before anything could render.
 *
 * EaglercraftX already has the answer: the assets are packed into assets.epk and unpacked
 * into a map keyed by path, which EagRuntime reads. That map is a better fit than the
 * classpath ever was, because enumerating a directory is a prefix scan rather than a
 * classloader walk.
 */
public class VanillaPackResources implements PackResources, ResourceProvider {

	private static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * Vanilla points this at a folder for `-Dminecraft.generatedDir` dumps of the built-in
	 * pack. There is nowhere to dump to here; it stays null and nothing reads it.
	 */
	public static Path generatedDir;

	public static Class<?> clientObject;

	public final PackMetadataSection packMetadata;
	public final Set<String> namespaces;

	public VanillaPackResources(PackMetadataSection packMetadata, String... namespaces) {
		this.packMetadata = packMetadata;
		this.namespaces = ImmutableSet.copyOf(namespaces);
	}

	/** "assets/minecraft/lang/en_us.json" - the same layout the EPK uses. */
	private static String pathOf(PackType type, ResourceLocation location) {
		return type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
	}

	@Override
	public InputStream getRootResource(String name) throws IOException {
		if (name.contains("/") || name.contains("\\")) {
			throw new IllegalArgumentException("Root resources can only be a single filename: "
					+ name);
		}
		return open(name);
	}

	@Override
	public InputStream getResource(PackType type, ResourceLocation location) throws IOException {
		return open(pathOf(type, location));
	}

	private InputStream open(String path) throws IOException {
		byte[] bytes = EagRuntime.getResourceBytes(path);
		if (bytes == null) {
			throw new FileNotFoundException(path);
		}
		return new ByteArrayInputStream(bytes);
	}

	protected InputStream getResourceAsStream(PackType type, ResourceLocation location) {
		byte[] bytes = EagRuntime.getResourceBytes(pathOf(type, location));
		return bytes == null ? null : new ByteArrayInputStream(bytes);
	}

	protected InputStream getResourceAsStream(String path) {
		byte[] bytes = EagRuntime.getResourceBytes(path);
		return bytes == null ? null : new ByteArrayInputStream(bytes);
	}

	@Override
	public boolean hasResource(PackType type, ResourceLocation location) {
		return EagRuntime.getResourceExists(pathOf(type, location));
	}

	/**
	 * Vanilla walks the classpath for this; here it is a prefix scan of the unpacked EPK.
	 * `depth` bounds how many path segments below `prefix` a result may be, and `filter`
	 * tests the file name only - both as vanilla defines them, so callers that rely on the
	 * depth limit (the resource-pack list, model loading) behave the same.
	 */
	@Override
	public Collection<ResourceLocation> getResources(PackType type, String namespace,
			String prefix, int depth, Predicate<String> filter) {
		String root = type.getDirectory() + "/" + namespace + "/";
		String base = root + prefix + "/";
		List<ResourceLocation> out = new ArrayList<>();
		for (String path : EagRuntime.getResourceList()) {
			if (!path.startsWith(base)) {
				continue;
			}
			String relative = path.substring(base.length());
			if (relative.isEmpty()) {
				continue;
			}
			// depth counts the segments below prefix; a file directly in it is depth 1
			int segments = 1;
			for (int i = 0; i < relative.length(); ++i) {
				if (relative.charAt(i) == '/') {
					++segments;
				}
			}
			if (segments > depth) {
				continue;
			}
			int slash = relative.lastIndexOf('/');
			String name = slash < 0 ? relative : relative.substring(slash + 1);
			if (filter.test(name)) {
				out.add(new ResourceLocation(namespace, path.substring(root.length())));
			}
		}
		return out;
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return namespaces;
	}

	@Override
	public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
		try (InputStream in = getRootResource("pack.mcmeta")) {
			if (in != null) {
				T section = AbstractPackResources.getMetadataFromStream(serializer, in);
				if (section != null) {
					return section;
				}
			}
		} catch (FileNotFoundException | RuntimeException ex) {
			// vanilla falls back to the metadata it was constructed with
		}
		@SuppressWarnings("unchecked")
		T fallback = serializer.getMetadataSectionName().equals("pack") ? (T) packMetadata : null;
		return fallback;
	}

	@Override
	public String getName() {
		return "Default";
	}

	@Override
	public void close() {
	}

	@Override
	public Resource getResource(ResourceLocation location) throws IOException {
		return new SimpleResource(getName(), location, getResource(PackType.CLIENT_RESOURCES,
				location), null);
	}
}
