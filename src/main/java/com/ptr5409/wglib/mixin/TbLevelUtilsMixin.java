package com.ptr5409.wglib.mixin;

import com.ptr5409.wglib.biomereplace.BiomeReplaceEngine;
import com.ptr5409.wglib.biomereplace.TbInjectContext;
import com.ptr5409.wglib.biomereplace.NoiseBiomeSourceAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 功能：TerraBlender 初始化群系时注入维度上下文并替换延迟群系列表
 */
@Pseudo
@Mixin(targets = "terrablender.util.LevelUtils", remap = false)
public abstract class TbLevelUtilsMixin {
    @Inject(method = "initializeBiomes", at = @At("HEAD"), remap = false)
    private static void ptrlib$beginTbContext(
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionType,
            ResourceKey<LevelStem> levelResourceKey,
            ChunkGenerator chunkGenerator,
            long seed,
            CallbackInfo ci) {
        String dim = levelResourceKey != null && levelResourceKey.location() != null
                ? levelResourceKey.location().toString()
                : null;
        NoiseBiomeSourceAccess source = chunkGenerator.getBiomeSource() instanceof NoiseBiomeSourceAccess access
                ? access
                : null;
        TbInjectContext.begin(dim, registryAccess, source);
    }

    @Inject(method = "initializeBiomes", at = @At("RETURN"), remap = false)
    private static void ptrlib$endTbContext(
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionType,
            ResourceKey<LevelStem> levelResourceKey,
            ChunkGenerator chunkGenerator,
            long seed,
            CallbackInfo ci) {
        TbInjectContext.end();
    }

    @ModifyArg(
            method = "initializeBiomes",
            at = @At(
                    value = "INVOKE",
                    target = "Lterrablender/worldgen/IExtendedBiomeSource;appendDeferredBiomesList(Ljava/util/List;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static List<Holder<Biome>> ptrlib$replaceDeferredList(List<Holder<Biome>> original) {
        return BiomeReplaceEngine.transformTbDeferredList(original, TbInjectContext.dimensionId());
    }
}
