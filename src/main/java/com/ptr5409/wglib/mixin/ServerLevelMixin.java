package com.ptr5409.wglib.mixin;

import com.ptr5409.wglib.dimcopy.DimCopyManager;
import com.ptr5409.wglib.dimreset.DimSeedOverrides;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/**
 * 功能：让副本/重置换种维度返回自定义种子
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {
    protected ServerLevelMixin(WritableLevelData data, ResourceKey<Level> dimension, RegistryAccess registryAccess,
                               Holder<DimensionType> dimensionType, Supplier<ProfilerFiller> profiler,
                               boolean isClient, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(data, dimension, registryAccess, dimensionType, profiler, isClient, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void ptrlib$customSeed(CallbackInfoReturnable<Long> cir) {
        Long seed = DimCopyManager.getSeed(this.dimension());
        if (seed == null) {
            seed = DimSeedOverrides.get(this.dimension());
        }
        if (seed != null) {
            cir.setReturnValue(seed);
        }
    }
}