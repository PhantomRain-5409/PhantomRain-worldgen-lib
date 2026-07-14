package com.ptr5409.wglib.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.Nullable;

public class BasicStructurePiece extends StructurePiece {

    /** 共享处理器：将结构方块替换为空气，避免每次放置新建匿名类 */
    private static final StructureProcessor STRIP_STRUCTURE_BLOCKS = new StructureProcessor() {
        @Nullable
        @Override
        public StructureTemplate.StructureBlockInfo processBlock(
                net.minecraft.world.level.LevelReader level,
                BlockPos pos,
                BlockPos pivot,
                StructureTemplate.StructureBlockInfo blockInfo,
                StructureTemplate.StructureBlockInfo relativeBlockInfo,
                StructurePlaceSettings settings) {
            if (relativeBlockInfo.state().is(Blocks.STRUCTURE_BLOCK)) {
                return new StructureTemplate.StructureBlockInfo(
                        relativeBlockInfo.pos(), Blocks.AIR.defaultBlockState(), null);
            }
            return relativeBlockInfo;
        }

        @Override
        protected StructureProcessorType<?> getType() {
            return StructureProcessorType.NOP;
        }
    };

    private StructureTemplate template;
    private final Rotation rotation;
    private BlockPos templatePosition;
    private final String templateLocation;
    private ResourceLocation templateId;

    public BasicStructurePiece(StructureTemplateManager manager, ResourceLocation nbtLocation,
                               BlockPos pos, Rotation rotation) {
        super(WGStructurePieceTypes.BASIC_PIECE.get(), 0, new BoundingBox(pos));
        this.template = manager.getOrCreate(nbtLocation);
        this.rotation = rotation;
        this.templatePosition = pos;
        this.templateLocation = nbtLocation.toString();
        this.templateId = nbtLocation;

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
        this.templateId = ResourceLocation.tryParse(this.templateLocation);
        this.templatePosition = new BlockPos(tag.getInt("PosX"), tag.getInt("PosY"), tag.getInt("PosZ"));
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

    private StructurePlaceSettings createSettings() {
        return new StructurePlaceSettings()
                .setRotation(this.rotation)
                .addProcessor(STRIP_STRUCTURE_BLOCKS);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        if (this.template == null && this.templateId != null) {
            this.template = level.getLevel().getStructureManager().getOrCreate(this.templateId);
        }

        if (this.template == null) {
            return;
        }

        Vec3i size = this.template.getSize();
        if (size.getX() > 0 && size.getY() > 0 && size.getZ() > 0) {
            this.template.placeInWorld(level, this.templatePosition, this.templatePosition,
                    createSettings(), random, 2);
        }
    }
}