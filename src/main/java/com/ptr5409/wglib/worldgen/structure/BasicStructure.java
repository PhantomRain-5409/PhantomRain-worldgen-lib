package com.ptr5409.wglib.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

public class BasicStructure extends Structure {

    public static final MapCodec<BasicStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Structure.settingsCodec(instance),
                    ResourceLocation.CODEC.fieldOf("nbt_path").forGetter(s -> s.nbtPath),
                    IntProvider.CODEC.optionalFieldOf("fixed_height").forGetter(s -> s.fixedHeight),
                    Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap", Heightmap.Types.WORLD_SURFACE_WG).forGetter(s -> s.projectStartToHeightmap),
                    Codec.INT.optionalFieldOf("y_offset", 0).forGetter(s -> s.yOffset)
            ).apply(instance, BasicStructure::new)
    );

    private final ResourceLocation nbtPath;
    private final Optional<IntProvider> fixedHeight;
    private final Heightmap.Types projectStartToHeightmap;
    private final int yOffset;

    public BasicStructure(StructureSettings settings, ResourceLocation nbtPath, Optional<IntProvider> fixedHeight, Heightmap.Types projectStartToHeightmap, int yOffset) {
        super(settings);
        this.nbtPath = nbtPath;
        this.fixedHeight = fixedHeight;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.yOffset = yOffset;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        ChunkGenerator chunkGenerator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();
        RandomState randomState = context.randomState();
        RandomSource random = context.random();

        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();

        int y;
        if (this.fixedHeight.isPresent()) {
            y = this.fixedHeight.get().sample(random);
        } else {
            y = chunkGenerator.getBaseHeight(x, z, this.projectStartToHeightmap, heightAccessor, randomState);
            y += this.yOffset;
        }

        BlockPos structurePos = new BlockPos(x, y, z);
        Rotation rotation = Rotation.getRandom(random);

        return Optional.of(new GenerationStub(structurePos, builder -> {
            builder.addPiece(new BasicStructurePiece(context.structureTemplateManager(), this.nbtPath, structurePos, rotation));
        }));
    }

    @Override
    public StructureType<?> type() {
        return WGStructureTypes.BASIC_STRUCTURE_TYPE.get();
    }
}
