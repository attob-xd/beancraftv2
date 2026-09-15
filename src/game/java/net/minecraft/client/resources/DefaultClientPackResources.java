package net.minecraft.client.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;

/**
 * Vanilla's class, with one namespace added: {@code eagler}.
 *
 * <p>1.18.2 resolves resources per namespace. {@code MultiPackResourceManager} builds one
 * {@code FallbackResourceManager} for each namespace the packs declare, and a
 * {@code ResourceLocation} whose namespace has no manager is a {@code FileNotFoundException} -
 * it never reaches a pack, so it does not matter in the slightest that the file is present.
 * Vanilla declares {@code minecraft} and {@code realms}, and nothing else can ever be declared,
 * because the set is a constructor argument that only this class passes.
 *
 * <p>EaglercraftX's own UI loads its artwork the ordinary way, through the resource manager:
 * {@code new ResourceLocation("eagler:gui/eagler_gui.png")} appears in the notification bell and
 * list, the server-notification renderer, the cape editor and the profile editor. Every one of
 * those got a {@code FileNotFoundException} and drew the missing-texture checkerboard, while
 * {@code assets/eagler/gui/eagler_gui.png} sat in {@code assets.epk} the whole time - which is
 * the confusing part of this bug, and the reason it reads as a packaging problem when it is not
 * one. {@code tools/list_epk.py javascript/assets.epk eagler/gui} shows the file is there.
 *
 * <p>1.12.2 had no such gate: its {@code DefaultResourcePack} took a namespace <i>set</i> built
 * from what the pack actually contained, so {@code eagler} was included by construction and
 * lax1dude never had to declare it. The namespace becoming a hardcoded pair is a 1.18.2 change,
 * so naming {@code eagler} here restores the behaviour his code was written against rather than
 * inventing anything.
 *
 * <p>Everything else is vanilla's, unchanged - including the {@code assetIndex} lookups, which
 * find nothing in a browser and fall through to {@code super}, exactly as they did before.
 */
public class DefaultClientPackResources extends VanillaPackResources {
	private final AssetIndex assetIndex;

	public DefaultClientPackResources(PackMetadataSection var1, AssetIndex var2) {
		super(var1, "minecraft", "realms", "eagler");
		this.assetIndex = var2;
	}

	@Nullable
	@Override
	protected InputStream getResourceAsStream(PackType var1, ResourceLocation var2) {
		if (var1 == PackType.CLIENT_RESOURCES) {
			File var3 = this.assetIndex.getFile(var2);
			if (var3 != null && var3.exists()) {
				try {
					return new FileInputStream(var3);
				} catch (FileNotFoundException var5) {
				}
			}
		}

		return super.getResourceAsStream(var1, var2);
	}

	@Override
	public boolean hasResource(PackType var1, ResourceLocation var2) {
		if (var1 == PackType.CLIENT_RESOURCES) {
			File var3 = this.assetIndex.getFile(var2);
			if (var3 != null && var3.exists()) {
				return true;
			}
		}

		return super.hasResource(var1, var2);
	}

	@Nullable
	@Override
	protected InputStream getResourceAsStream(String var1) {
		File var2 = this.assetIndex.getRootFile(var1);
		if (var2 != null && var2.exists()) {
			try {
				return new FileInputStream(var2);
			} catch (FileNotFoundException var4) {
			}
		}

		return super.getResourceAsStream(var1);
	}

	@Override
	public Collection<ResourceLocation> getResources(PackType var1, String var2, String var3, int var4, Predicate<String> var5) {
		Collection var6 = super.getResources(var1, var2, var3, var4, var5);
		var6.addAll(this.assetIndex.getFiles(var3, var2, var4, var5));
		return var6;
	}
}
