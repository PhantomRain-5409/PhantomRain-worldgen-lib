package com.ptr5409.wglib.mixin;

import com.google.common.collect.ImmutableList;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import com.ptr5409.wglib.accessor.MinecraftServerAccessor;
import com.ptr5409.wglib.dimcopy.DimCopyManager;
import com.ptr5409.wglib.dimreset.DimSeedOverrides;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 功能：动态创建/卸载维度，并在世界加载时恢复副本维度
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends BlockableEventLoop<TickTask> implements MinecraftServerAccessor {
    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow
    @Final
    private Executor executor;

    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Shadow
    @Final
    protected WorldData worldData;

    @Shadow(remap = false)
    public abstract void markWorldsDirty();

    protected MinecraftServerMixin(String name) {
        super(name);
    }

    @Unique
    private MinecraftServer ptrlib$self() {
        return (MinecraftServer) (Object) this;
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void ptrlib$loadDimCopies(ChunkProgressListener listener, CallbackInfo ci) {
        DimCopyManager.bootstrap(this.ptrlib$self());
        DimSeedOverrides.bootstrap(this.ptrlib$self());
        DimCopyManager.loadAll(this.ptrlib$self());
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void ptrlib$onStop(CallbackInfo ci) {
        DimCopyManager.save(this.ptrlib$self());
        DimSeedOverrides.save(this.ptrlib$self());
        DimCopyManager.clear();
        DimSeedOverrides.clear();
    }

    @Override
    public boolean ptrlib$createLevel(ResourceKey<Level> key, LevelStem stem, long seed) {
        try {
            ServerLevelData overworldData = this.worldData.overworldData();
            boolean debug = this.worldData.isDebugWorld();
            DerivedLevelData derived = new DerivedLevelData(this.worldData, overworldData);
            long biomeSeed = BiomeManager.obfuscateSeed(seed);
            ServerLevel level = new ServerLevel(
                    this.ptrlib$self(),
                    this.executor,
                    this.storageSource,
                    derived,
                    key,
                    stem,
                    new StoringChunkProgressListener(11),
                    debug,
                    biomeSeed,
                    ImmutableList.of(),
                    false,
                    null
            );
            this.levels.put(key, level);
            this.markWorldsDirty();
            return true;
        } catch (Exception e) {
            PhantomRainWorldgenLib.LOGGER.error("Failed to create dimension copy {}", key.location(), e);
            return false;
        }
    }

    @Override
    public void ptrlib$removeLevel(ResourceKey<Level> key) {
        this.levels.remove(key);
        this.markWorldsDirty();
    }
}
