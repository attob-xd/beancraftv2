package net.peyton.eagler.minecraft;

import net.minecraft.world.level.Level;

public interface EntityConstructor<T> {

	T createEntity(Level world);

}