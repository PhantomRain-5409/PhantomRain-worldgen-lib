package com.ptr5409.wglib.biomereplace;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;

/**
 * 功能：TerraBlender 初始化时传递当前维度/注册表上下文
 */
public final class TbInjectContext {
    private static final ThreadLocal<String> DIMENSION = new ThreadLocal<>();
    private static final ThreadLocal<RegistryAccess> REGISTRIES = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> TREE_INDEX = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<NoiseBiomeSourceAccess> BIOME_SOURCE = new ThreadLocal<>();

    private TbInjectContext() {
    }

    public static void begin(String dimensionId, RegistryAccess registryAccess,
                             NoiseBiomeSourceAccess biomeSource) {
        DIMENSION.set(dimensionId);
        REGISTRIES.set(registryAccess);
        ACTIVE.set(true);
        TREE_INDEX.set(0);
        BIOME_SOURCE.set(biomeSource);
    }

    public static void end() {
        ACTIVE.set(false);
        DIMENSION.remove();
        REGISTRIES.remove();
        ACTIVE.remove();
        TREE_INDEX.remove();
        BIOME_SOURCE.remove();
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static String dimensionId() {
        return DIMENSION.get();
    }

    public static RegistryAccess registryAccess() {
        return REGISTRIES.get();
    }

    public static Registry<Biome> biomeRegistry() {
        RegistryAccess access = REGISTRIES.get();
        return access == null ? null : access.registryOrThrow(Registries.BIOME);
    }

    public static boolean shouldTransformTree() {
        int index = TREE_INDEX.get();
        TREE_INDEX.set(index + 1);
        return index > 0;
    }

    public static void appendSubBiomeParameters(
            java.util.List<com.mojang.datafixers.util.Pair<
                    net.minecraft.world.level.biome.Climate.ParameterPoint, Holder<Biome>>> original,
            java.util.List<com.mojang.datafixers.util.Pair<
                    net.minecraft.world.level.biome.Climate.ParameterPoint, Holder<Biome>>> effective) {
        NoiseBiomeSourceAccess source = BIOME_SOURCE.get();
        if (source != null) {
            source.ptrlib$appendSubBiomeParameters(original, effective);
        }
    }
}
