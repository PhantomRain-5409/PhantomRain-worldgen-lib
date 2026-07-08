package com.ptr5409.wglib.worldgen;

import com.ptr5409.wglib.PhantomRainWorldgenLib;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class WGStructureTypes {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.STRUCTURE_TYPE, PhantomRainWorldgenLib.MOD_ID);

    // 注册 basic 结构类型，使用 .codec() 将 MapCodec 转换为 Codec
    public static final RegistryObject<StructureType<BasicStructure>> BASIC_STRUCTURE_TYPE =
            STRUCTURE_TYPES.register("basic", () -> () -> BasicStructure.CODEC.codec());
}
