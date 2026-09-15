package net.peyton.eagler.minecraft;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

public interface DragonPhaseConstructor<T> {

	T createPhase(EnderDragon phase);

}