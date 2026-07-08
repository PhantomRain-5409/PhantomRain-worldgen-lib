package com.ptr5409.wglib.worldgen;

import com.mojang.serialization.Codec;
import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class WGChunkGenerators {
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, PhantomRainWorldgenLib.MOD_ID);

    public static final RegistryObject<Codec<? extends ChunkGenerator>> PRESET_GENERATOR =
            CHUNK_GENERATORS.register("preset", () -> PresetChunkGenerator.CODEC);
}
