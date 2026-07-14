package com.ptr5409.wglib.worldgen.generator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class PresetChunkGenerator extends ChunkGenerator {

    private static final BlockState[] EMPTY_COLUMN = new BlockState[0];
    private static final NoiseColumn EMPTY_NOISE_COLUMN = new NoiseColumn(0, EMPTY_COLUMN);

    public static final Codec<PresetChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Biome.CODEC.fieldOf("default_biome")
                            .forGetter(gen -> gen.biome)
            ).apply(instance, PresetChunkGenerator::new)
    );

    private final Holder<Biome> biome;

    public PresetChunkGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion arg0, long arg1, RandomState arg2, BiomeManager arg3,
                             StructureManager arg4, ChunkAccess arg5, GenerationStep.Carving arg6) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures,
                             RandomState random, ChunkAccess chunk) {
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return 0;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return EMPTY_NOISE_COLUMN;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }
}