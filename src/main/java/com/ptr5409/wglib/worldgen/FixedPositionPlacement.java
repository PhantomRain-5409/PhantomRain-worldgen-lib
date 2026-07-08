package com.ptr5409.wglib.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class FixedPositionPlacement extends StructurePlacement {

    // 编解码器：仅包含 x 和 z，默认值为 0
    public static final MapCodec<FixedPositionPlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("x").orElse(0).forGetter(p -> p.x),
                    Codec.INT.fieldOf("z").orElse(0).forGetter(p -> p.z)
            ).apply(instance, FixedPositionPlacement::new)
    );

    private final int x;
    private final int z;

    public FixedPositionPlacement(int x, int z) {
        super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1, 0, Optional.empty());
        this.x = x;
        this.z = z;
    }

    @Override
    protected boolean isPlacementChunk(@NotNull ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return chunkX == this.x && chunkZ == this.z;
    }

    @Override
    public boolean isStructureChunk(@NotNull ChunkGeneratorStructureState structureState, int x, int z) {
        return isPlacementChunk(structureState, x, z);
    }

    @Override
    public @NotNull StructurePlacementType<?> type() {
        return WGPlacementTypes.FIXED_POSITION_PLACEMENT.get();
    }
}
