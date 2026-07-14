package com.ptr5409.wglib.worldgen.structure;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class WGPlacementTypes {
    public static final DeferredRegister<StructurePlacementType<?>> STRUCTURE_PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, PhantomRainWorldgenLib.MOD_ID);
    public static final RegistryObject<StructurePlacementType<FixedPositionPlacement>> FIXED_POSITION_PLACEMENT =
            STRUCTURE_PLACEMENT_TYPES.register("fixed_position", () -> FixedPositionPlacement.CODEC::codec);
}
