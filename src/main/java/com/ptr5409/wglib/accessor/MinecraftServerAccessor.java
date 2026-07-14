package com.ptr5409.wglib.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * 功能：向 MinecraftServer 暴露动态创建/移除维度的能力
 */
public interface MinecraftServerAccessor {
    boolean ptrlib$createLevel(ResourceKey<Level> key, LevelStem stem, long seed);

    void ptrlib$removeLevel(ResourceKey<Level> key);
}
