package com.ptr5409.wglib.mixin;

import com.ptr5409.wglib.biomereplace.BiomeReplaceEngine;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 功能：世界注册表就绪后应用数据包群系替换
 */
@Mixin(WorldStem.class)
public abstract class WorldStemMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ptrlib$applyBiomeReplace(
            CloseableResourceManager resourceManager,
            ReloadableServerResources dataPackResources,
            LayeredRegistryAccess<RegistryLayer> registries,
            WorldData worldData,
            CallbackInfo ci) {
        RegistryAccess.Frozen access = registries.compositeAccess();
        BiomeReplaceEngine.apply(
                resourceManager,
                access.registryOrThrow(Registries.BIOME),
                access.registryOrThrow(Registries.LEVEL_STEM),
                worldData.worldGenOptions().seed()
        );
    }
}
