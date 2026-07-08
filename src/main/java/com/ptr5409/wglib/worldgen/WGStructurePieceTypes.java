package com.ptr5409.wglib.worldgen;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class WGStructurePieceTypes {
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, PhantomRainWorldgenLib.MOD_ID);

    public static final RegistryObject<StructurePieceType> BASIC_PIECE =
            PIECE_TYPES.register("basic_piece", () -> BasicStructurePiece::new);
}
