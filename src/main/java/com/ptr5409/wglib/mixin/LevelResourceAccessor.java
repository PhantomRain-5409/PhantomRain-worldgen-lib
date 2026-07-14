package com.ptr5409.wglib.mixin;

import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 功能：调用 LevelResource 私有构造，用于自定义存档子路径
 */
@Mixin(LevelResource.class)
public interface LevelResourceAccessor {
    @Invoker("<init>")
    static LevelResource ptrlib$create(String relativePath) {
        throw new AssertionError();
    }
}
