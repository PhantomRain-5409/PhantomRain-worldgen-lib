package com.ptr5409.wglib.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

public class BasicStructurePiece extends StructurePiece {

    private StructureTemplate template;
    private final Rotation rotation;
    private BlockPos templatePosition;
    private String templateLocation;

    public BasicStructurePiece(StructureTemplateManager manager, ResourceLocation nbtLocation,
                               BlockPos pos, Rotation rotation) {
        super(WGStructurePieceTypes.BASIC_PIECE.get(), 0, new BoundingBox(pos));
        this.template = manager.getOrCreate(nbtLocation);
        this.rotation = rotation;
        this.templatePosition = pos;
        this.templateLocation = nbtLocation.toString();

        StructurePlaceSettings settings = createSettings();
        Vec3i size = this.template.getSize();
        if (size.getX() > 0 && size.getY() > 0 && size.getZ() > 0) {
            this.boundingBox = this.template.getBoundingBox(settings, this.templatePosition);
        } else {
            this.boundingBox = new BoundingBox(this.templatePosition);
        }
    }

    public BasicStructurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(WGStructurePieceTypes.BASIC_PIECE.get(), tag);
        this.template = null;
        this.rotation = Rotation.valueOf(tag.getString("Rot"));
        this.templateLocation = tag.getString("Template");

        int x = tag.getInt("PosX");
        int y = tag.getInt("PosY");
        int z = tag.getInt("PosZ");
        this.templatePosition = new BlockPos(x, y, z);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("Template", this.templateLocation != null ? this.templateLocation : "minecraft:empty");
        tag.putString("Rot", this.rotation.name());
        if (this.templatePosition != null) {
            tag.putInt("PosX", this.templatePosition.getX());
            tag.putInt("PosY", this.templatePosition.getY());
            tag.putInt("PosZ", this.templatePosition.getZ());
        }
    }

    // 抽取一个生成 Settings 的公共方法，并挂载拦截处理器
    private StructurePlaceSettings createSettings() {
        return new StructurePlaceSettings()
                .setRotation(this.rotation)
                .addProcessor(new StructureProcessor() {
                    @Nullable
                    @Override
                    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos pos, BlockPos pivot, StructureTemplate.StructureBlockInfo blockInfo, StructureTemplate.StructureBlockInfo relativeBlockInfo, StructurePlaceSettings settings) {
                        // 如果即将放置的方块是结构方块，直接返回空气
                        if (relativeBlockInfo.state().is(Blocks.STRUCTURE_BLOCK)) {
                            return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), Blocks.AIR.defaultBlockState(), null);
                        }
                        return relativeBlockInfo;
                    }

                    @Override
                    protected StructureProcessorType<?> getType() {
                        return StructureProcessorType.NOP; // 使用原版内置的空处理器类型即可
                    }
                });
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        if (this.template == null && this.templateLocation != null) {
            StructureTemplateManager templateManager = level.getLevel().getStructureManager();
            this.template = templateManager.getOrCreate(new ResourceLocation(this.templateLocation));
        }

        if (this.template != null) {
            Vec3i size = this.template.getSize();
            if (size.getX() > 0 && size.getY() > 0 && size.getZ() > 0) {
                StructurePlaceSettings settings = createSettings();
                this.template.placeInWorld(level, this.templatePosition, this.templatePosition,
                        settings, random, 2);
            }
        }
    }
}
