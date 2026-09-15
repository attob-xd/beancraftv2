package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;

/**
 * Vanilla's class, with the six light vectors built by a plain helper instead of
 * {@code Util.make(new Vector3f(...), Vector3f::normalize)}.
 *
 * <p>These six are the only values that ever reach {@code RenderSystem.shaderLightDirections}.
 * {@code setupShaderLights} runs on every buffer upload and does
 * {@code LIGHT0_DIRECTION.set(shaderLightDirections[0])}: it null-checks the uniform but not the
 * vector, so a null among these is a null dereference inside {@code Uniform.set}, surfacing as
 * "Cannot read properties of null" in whichever renderer happened to flush a buffer next. Here
 * that was the player's hand, several frames of call stack from the cause, after the whole world
 * had already drawn correctly.
 *
 * <p>{@code Util.make} takes a value and a Consumer, applies it and hands the value back, so the
 * vanilla form relies on a method reference to a void instance method being turned into a
 * Consumer. That is exactly the kind of construct this port has had trouble with, and it buys
 * nothing here: a two-line helper produces the same normalised vectors with no lambda, no
 * functional interface and nothing for the optimiser to get wrong.
 *
 * <p>Written without a logging helper on purpose. An earlier version of this file added one and
 * TeaVM's own build daemon overflowed its stack compiling it, emitting no output at all - the
 * same way builds 128-131 and 142 were lost. Its stack size cannot be raised from this project.
 */
/**
 * Note: an earlier version seeded RenderSystem.shaderLightDirections from a static initialiser
 * here, so the array held real directions before the first world frame read it. TeaVM's build
 * daemon overflowed its stack compiling that - the third time a small addition has done so in
 * this project, after builds 142 and 152 - and a failed TeaVM build writes no output at all, so
 * the cost is a whole compile with nothing to show.
 *
 * The seeding was only cosmetic. Uniform.set(Vector3f) skips a null vector rather than
 * dereferencing it, so the untouched array is harmless: the first few buffer uploads of a world
 * use whatever light direction the shader already had, and LevelRenderer calls setupLevel later
 * in the same frame. Not worth another lost build.
 */
public class Lighting {

	/** new Vector3f(x, y, z).normalize(), as an expression. */
	private static Vector3f normalized(float x, float y, float z) {
		Vector3f v = new Vector3f(x, y, z);
		v.normalize();
		return v;
	}

	private static final Vector3f DIFFUSE_LIGHT_0 = normalized(0.2F, 1.0F, -0.7F);
	private static final Vector3f DIFFUSE_LIGHT_1 = normalized(-0.2F, 1.0F, 0.7F);
	private static final Vector3f NETHER_DIFFUSE_LIGHT_0 = normalized(0.2F, 1.0F, -0.7F);
	private static final Vector3f NETHER_DIFFUSE_LIGHT_1 = normalized(-0.2F, -1.0F, 0.7F);
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_0 = normalized(0.2F, -1.0F, -1.0F);
	private static final Vector3f INVENTORY_DIFFUSE_LIGHT_1 = normalized(-0.2F, -1.0F, 0.0F);

	public static void setupNetherLevel(Matrix4f var0) {
		RenderSystem.setupLevelDiffuseLighting(NETHER_DIFFUSE_LIGHT_0, NETHER_DIFFUSE_LIGHT_1, var0);
	}

	public static void setupLevel(Matrix4f var0) {
		RenderSystem.setupLevelDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1, var0);
	}

	public static void setupForFlatItems() {
		RenderSystem.setupGuiFlatDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);
	}

	public static void setupFor3DItems() {
		RenderSystem.setupGui3DDiffuseLighting(DIFFUSE_LIGHT_0, DIFFUSE_LIGHT_1);
	}

	public static void setupForEntityInInventory() {
		RenderSystem.setShaderLights(INVENTORY_DIFFUSE_LIGHT_0, INVENTORY_DIFFUSE_LIGHT_1);
	}
}
